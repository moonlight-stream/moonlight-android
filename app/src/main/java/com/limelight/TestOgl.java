package com.limelight;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.limelight.binding.video.EnhancedDecoderRenderer;
import com.limelight.nvstream.NvConnection;
import com.limelight.ogl.VideoTextureRenderer;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.SpinnerDialog;

import java.io.IOException;
import java.util.Locale;

public class TestOgl extends Activity implements TextureView.SurfaceTextureListener, View.OnSystemUiVisibilityChangeListener
{
    private static final String LOG_TAG = "SurfaceTest";

    private PreferenceConfiguration prefConfig;
    private NvConnection conn;
    private SpinnerDialog spinner;
    private boolean displayedFailureDialog = false;
    private boolean connecting = false;
    private boolean connected = true;
    private TextureView textureView;
    private EnhancedDecoderRenderer decoderRenderer;
    private WifiManager.WifiLock wifiLock;
    private int drFlags = 0;

    public static final String EXTRA_HOST = "Host";
    public static final String EXTRA_APP_NAME = "AppName";
    public static final String EXTRA_APP_ID = "AppId";
    public static final String EXTRA_UNIQUEID = "UniqueId";
    public static final String EXTRA_STREAMING_REMOTE = "Remote";


    private TextureView surface;
    private MediaPlayer player;
    private VideoTextureRenderer renderer;

    private int surfaceWidth;
    private int surfaceHeight;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
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
        //setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_testogl);

        surface = (TextureView) findViewById(R.id.surface);
        surface.setSurfaceTextureListener(this);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (surface.isAvailable()) {
            startPlaying();
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (player != null)
            player.release();
        if (renderer != null)
            renderer.onPause();
    }

    private void startPlaying()
    {
        renderer = null;//new VideoTextureRenderer(this, surface.getSurfaceTexture(), surfaceWidth, surfaceHeight);
        player = new MediaPlayer();

        try
        {
            player.setDataSource("http://hwcdn.net/j9t9v3v5/cds/Foreman_H264.mp4");
            player.setSurface(new Surface(renderer.getVideoTexture()));
            player.setLooping(true);
            player.prepare();
            renderer.setVideoSize(player.getVideoWidth(), player.getVideoHeight());
            player.start();

        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not open input video!");
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
    {
        surfaceWidth = width;
        surfaceHeight = height;
        startPlaying();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
    {
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

    @SuppressLint("InlinedApi")
    private final Runnable hideSystemUi = new Runnable() {
        @Override
        public void run() {
            TestOgl.this.getWindow().getDecorView().setSystemUiVisibility(
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
}