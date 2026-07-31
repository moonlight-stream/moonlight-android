package com.limelight.binding.video;

import android.content.Context;

import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.SbsCalibrationSnapshot;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class SbsCalibrationController {
    public interface LiveSnapshotListener {
        void onLiveSnapshotChanged(SbsCalibrationSnapshot snapshot);

        void onHeadTrackingRecenterRequested();
    }

    private final Context applicationContext;
    private final AtomicReference<SbsCalibrationSnapshot> liveSnapshot;
    private final AtomicLong latestPreviewRevision = new AtomicLong(Long.MIN_VALUE);
    private final CopyOnWriteArraySet<LiveSnapshotListener> liveSnapshotListeners =
            new CopyOnWriteArraySet<>();
    private volatile SbsCalibrationSnapshot savedSnapshot;

    public SbsCalibrationController(Context context) {
        applicationContext = context.getApplicationContext();
        savedSnapshot = PreferenceConfiguration.readSbsCalibrationPreferences(applicationContext);
        liveSnapshot = new AtomicReference<>(savedSnapshot);
    }

    public SbsCalibrationSnapshot getLiveSnapshot() {
        return liveSnapshot.get();
    }

    public void addLiveSnapshotListener(LiveSnapshotListener listener) {
        liveSnapshotListeners.add(listener);
    }

    public void removeLiveSnapshotListener(LiveSnapshotListener listener) {
        liveSnapshotListeners.remove(listener);
    }

    public boolean preview(SbsCalibrationSnapshot snapshot, long revision) {
        long previous;
        do {
            previous = latestPreviewRevision.get();
            if (revision <= previous) {
                return false;
            }
        } while (!latestPreviewRevision.compareAndSet(previous, revision));

        publishLiveSnapshot(snapshot);
        return true;
    }

    public synchronized SbsCalibrationSnapshot save(SbsCalibrationSnapshot snapshot) {
        if (!PreferenceConfiguration.writeSbsCalibrationPreferences(applicationContext, snapshot)) {
            throw new IllegalStateException("Unable to persist SBS calibration");
        }
        savedSnapshot = snapshot;
        publishLiveSnapshot(snapshot);
        return snapshot;
    }

    public SbsCalibrationSnapshot revert() {
        SbsCalibrationSnapshot snapshot = savedSnapshot;
        publishLiveSnapshot(snapshot);
        return snapshot;
    }

    public SbsCalibrationSnapshot reset() {
        SbsCalibrationSnapshot snapshot = SbsCalibrationSnapshot.defaults();
        publishLiveSnapshot(snapshot);
        return snapshot;
    }

    public void requestHeadTrackingRecenter() {
        for (LiveSnapshotListener listener : liveSnapshotListeners) {
            listener.onHeadTrackingRecenterRequested();
        }
    }

    public synchronized void reloadSavedSnapshot() {
        SbsCalibrationSnapshot snapshot =
                PreferenceConfiguration.readSbsCalibrationPreferences(applicationContext);
        savedSnapshot = snapshot;
        publishLiveSnapshot(snapshot);
    }

    private void publishLiveSnapshot(SbsCalibrationSnapshot snapshot) {
        liveSnapshot.set(snapshot);
        for (LiveSnapshotListener listener : liveSnapshotListeners) {
            listener.onLiveSnapshotChanged(snapshot);
        }
    }
}
