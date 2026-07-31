package com.limelight.binding.video;

import android.content.Context;

import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.SbsCalibrationSnapshot;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class SbsCalibrationController {
    private final Context applicationContext;
    private final AtomicReference<SbsCalibrationSnapshot> liveSnapshot;
    private final AtomicLong latestPreviewRevision = new AtomicLong(Long.MIN_VALUE);
    private volatile SbsCalibrationSnapshot savedSnapshot;

    public SbsCalibrationController(Context context) {
        applicationContext = context.getApplicationContext();
        savedSnapshot = PreferenceConfiguration.readSbsCalibrationPreferences(applicationContext);
        liveSnapshot = new AtomicReference<>(savedSnapshot);
    }

    public SbsCalibrationSnapshot getLiveSnapshot() {
        return liveSnapshot.get();
    }

    public boolean preview(SbsCalibrationSnapshot snapshot, long revision) {
        long previous;
        do {
            previous = latestPreviewRevision.get();
            if (revision <= previous) {
                return false;
            }
        } while (!latestPreviewRevision.compareAndSet(previous, revision));

        liveSnapshot.set(snapshot);
        return true;
    }

    public synchronized SbsCalibrationSnapshot save(SbsCalibrationSnapshot snapshot) {
        if (!PreferenceConfiguration.writeSbsCalibrationPreferences(applicationContext, snapshot)) {
            throw new IllegalStateException("Unable to persist SBS calibration");
        }
        savedSnapshot = snapshot;
        liveSnapshot.set(snapshot);
        return snapshot;
    }

    public SbsCalibrationSnapshot revert() {
        SbsCalibrationSnapshot snapshot = savedSnapshot;
        liveSnapshot.set(snapshot);
        return snapshot;
    }

    public SbsCalibrationSnapshot reset() {
        SbsCalibrationSnapshot snapshot = SbsCalibrationSnapshot.defaults();
        liveSnapshot.set(snapshot);
        return snapshot;
    }

    public synchronized void reloadSavedSnapshot() {
        SbsCalibrationSnapshot snapshot =
                PreferenceConfiguration.readSbsCalibrationPreferences(applicationContext);
        savedSnapshot = snapshot;
        liveSnapshot.set(snapshot);
    }
}
