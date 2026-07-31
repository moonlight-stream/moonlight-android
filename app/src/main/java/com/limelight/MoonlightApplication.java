package com.limelight;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import com.limelight.binding.video.SbsCalibrationController;
import com.limelight.binding.video.SbsCalibrationServer;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.concurrent.CopyOnWriteArraySet;

public final class MoonlightApplication extends Application implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    public interface CalibrationServerStatusListener {
        void onCalibrationServerStatusChanged(SbsCalibrationServer.Status status);
    }

    private final CopyOnWriteArraySet<CalibrationServerStatusListener> statusListeners =
            new CopyOnWriteArraySet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SbsCalibrationController calibrationController;
    private SbsCalibrationServer calibrationServer;
    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        calibrationController = new SbsCalibrationController(this);
        int port = readServerPort();
        calibrationServer = new SbsCalibrationServer(this, calibrationController,
                this::notifyServerStatus, port);
        preferences.registerOnSharedPreferenceChangeListener(this);
        applyServerPreferences();
    }

    public static MoonlightApplication from(Context context) {
        return (MoonlightApplication) context.getApplicationContext();
    }

    public SbsCalibrationController getCalibrationController() {
        return calibrationController;
    }

    public SbsCalibrationServer.Status getCalibrationServerStatus() {
        return calibrationServer.getStatus();
    }

    public void addCalibrationServerStatusListener(CalibrationServerStatusListener listener) {
        statusListeners.add(listener);
        listener.onCalibrationServerStatusChanged(calibrationServer.getStatus());
    }

    public void removeCalibrationServerStatusListener(CalibrationServerStatusListener listener) {
        statusListeners.remove(listener);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PreferenceConfiguration.ENABLE_SBS_CALIBRATION_SERVER_PREF_STRING.equals(key) ||
                PreferenceConfiguration.SBS_CALIBRATION_SERVER_PORT_PREF_STRING.equals(key)) {
            applyServerPreferences();
        } else if (PreferenceConfiguration.isSbsCalibrationPreference(key)) {
            calibrationController.reloadSavedSnapshot();
        }
    }

    private void applyServerPreferences() {
        int port = readServerPort();
        if (preferences.getBoolean(
                PreferenceConfiguration.ENABLE_SBS_CALIBRATION_SERVER_PREF_STRING,
                PreferenceConfiguration.DEFAULT_ENABLE_SBS_CALIBRATION_SERVER)) {
            calibrationServer.start(port);
        } else {
            calibrationServer.stop(port);
        }
    }

    private int readServerPort() {
        String value = preferences.getString(
                PreferenceConfiguration.SBS_CALIBRATION_SERVER_PORT_PREF_STRING,
                Integer.toString(PreferenceConfiguration.DEFAULT_SBS_CALIBRATION_SERVER_PORT));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void notifyServerStatus(SbsCalibrationServer.Status status) {
        mainHandler.post(() -> {
            LimeLog.info("SBS calibration server: " + status.detail);
            for (CalibrationServerStatusListener listener : statusListeners) {
                listener.onCalibrationServerStatusChanged(status);
            }
        });
    }
}
