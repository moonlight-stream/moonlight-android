package com.limelight.ui.console;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.limelight.R;

public final class UiFeedbackManager {
    private static final long FOCUS_DEBOUNCE_MS = 60;

    private final Context context;
    private final SoundPool soundPool;
    private final int focusSound;
    private final int confirmSound;
    private final int backSound;
    private final int errorSound;
    private long lastFocusAt;
    private boolean initialFocusConsumed;

    public UiFeedbackManager(Context context) {
        this.context = context.getApplicationContext();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(attributes)
                .build();
        focusSound = soundPool.load(context, R.raw.ui_focus, 1);
        confirmSound = soundPool.load(context, R.raw.ui_confirm, 1);
        backSound = soundPool.load(context, R.raw.ui_back, 1);
        errorSound = soundPool.load(context, R.raw.ui_error, 1);
    }

    public void focus(View view) {
        if (!initialFocusConsumed) {
            initialFocusConsumed = true;
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastFocusAt < FOCUS_DEBOUNCE_MS) {
            return;
        }
        lastFocusAt = now;
        play(focusSound, 0.28f);
        haptic(view, HapticFeedbackConstants.CLOCK_TICK);
    }

    public void confirm(View view) {
        play(confirmSound, 0.42f);
        haptic(view, Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ?
                HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.KEYBOARD_TAP);
    }

    public void back(View view) {
        play(backSound, 0.34f);
    }

    public void error(View view) {
        play(errorSound, 0.45f);
        haptic(view, Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ?
                HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS);
    }

    private void play(int soundId, float volume) {
        if (!LauncherUiPreferences.read(context).interfaceSounds ||
                !systemSoundsEnabled() || isHandheldSilent()) {
            return;
        }
        soundPool.play(soundId, volume, volume, 1, 0, 1f);
    }

    private void haptic(View view, int constant) {
        if (view != null && LauncherUiPreferences.read(context).navigationHaptics) {
            view.performHapticFeedback(constant);
        }
    }

    private boolean systemSoundsEnabled() {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0;
    }

    private boolean isHandheldSilent() {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return manager != null && context.getPackageManager().hasSystemFeature(
                "android.hardware.telephony") &&
                manager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
    }

    public void release() {
        soundPool.release();
    }
}
