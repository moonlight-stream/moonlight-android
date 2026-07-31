package com.limelight.binding.video;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.limelight.LimeLog;
import com.limelight.preferences.SbsCalibrationSnapshot;

import java.util.concurrent.atomic.AtomicReference;

public final class SbsHeadTracker implements SbsCalibrationController.LiveSnapshotListener {
    public static final class Pose {
        static final Pose CENTERED = new Pose(0.0f, 0.0f);

        public final float yawDegrees;
        public final float pitchDegrees;

        private Pose(float yawDegrees, float pitchDegrees) {
            this.yawDegrees = yawDegrees;
            this.pitchDegrees = pitchDegrees;
        }
    }

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;
    private final SbsCalibrationController calibrationController;
    private final AtomicReference<Pose> pose = new AtomicReference<>(Pose.CENTERED);
    private final float[] baselineMatrix = new float[9];
    private final float[] currentMatrix = new float[9];
    private final float[] orientationDegrees = new float[2];

    private HandlerThread sensorThread;
    private volatile Handler sensorHandler;
    private SensorEventListener activeSensorListener;
    private volatile Runnable poseChangedListener;
    private volatile boolean running;
    private int displayRotation;
    private volatile int generation;
    private boolean sensorRegistered;
    private boolean baselineValid;

    public SbsHeadTracker(Context context, SbsCalibrationController calibrationController) {
        sensorManager = (SensorManager) context.getApplicationContext()
                .getSystemService(Context.SENSOR_SERVICE);
        Sensor gameRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        rotationSensor = gameRotationSensor != null ? gameRotationSensor :
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        this.calibrationController = calibrationController;
    }

    public synchronized void start(int displayRotation, Runnable poseChangedListener) {
        if (running) {
            return;
        }

        running = true;
        generation++;
        int startGeneration = generation;
        this.displayRotation = displayRotation;
        this.poseChangedListener = poseChangedListener;
        sensorThread = new HandlerThread("Input - SBS Head Tracker");
        sensorThread.start();
        sensorHandler = new Handler(sensorThread.getLooper());
        calibrationController.addLiveSnapshotListener(this);
        sensorHandler.post(() -> applyCalibration(
                calibrationController.getLiveSnapshot(), startGeneration));
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;
        generation++;
        calibrationController.removeLiveSnapshotListener(this);
        if (activeSensorListener != null) {
            sensorManager.unregisterListener(activeSensorListener);
            activeSensorListener = null;
        }
        sensorRegistered = false;
        baselineValid = false;
        pose.set(Pose.CENTERED);
        poseChangedListener = null;

        HandlerThread thread = sensorThread;
        sensorThread = null;
        sensorHandler = null;
        if (thread != null) {
            thread.quitSafely();
        }
    }

    public Pose getPose() {
        return pose.get();
    }

    @Override
    public void onLiveSnapshotChanged(SbsCalibrationSnapshot snapshot) {
        Handler handler = sensorHandler;
        int currentGeneration = generation;
        if (running && handler != null) {
            handler.post(() -> applyCalibration(snapshot, currentGeneration));
        }
    }

    @Override
    public void onHeadTrackingRecenterRequested() {
        Handler handler = sensorHandler;
        int currentGeneration = generation;
        if (running && handler != null) {
            handler.post(() -> {
                if (isCurrentGeneration(currentGeneration)) {
                    baselineValid = false;
                    publishPose(Pose.CENTERED, currentGeneration);
                }
            });
        }
    }

    private synchronized void applyCalibration(SbsCalibrationSnapshot snapshot,
                                               int currentGeneration) {
        if (!isCurrentGeneration(currentGeneration)) {
            return;
        }

        boolean shouldTrack = snapshot.headTrackingEnabled &&
                (snapshot.headTrackingHorizontalEnabled || snapshot.headTrackingVerticalEnabled);
        if (shouldTrack && !sensorRegistered) {
            registerSensor(currentGeneration);
        } else if (!shouldTrack && sensorRegistered) {
            sensorManager.unregisterListener(activeSensorListener);
            activeSensorListener = null;
            sensorRegistered = false;
            baselineValid = false;
            publishPose(Pose.CENTERED, currentGeneration);
        } else {
            notifyPoseChanged(currentGeneration);
        }
    }

    private void registerSensor(int currentGeneration) {
        if (rotationSensor == null) {
            LimeLog.warning("No rotation vector sensor is available for SBS head tracking");
            publishPose(Pose.CENTERED, currentGeneration);
            return;
        }

        baselineValid = false;
        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                handleSensorChanged(event, currentGeneration);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        activeSensorListener = listener;
        sensorRegistered = sensorManager.registerListener(listener, rotationSensor,
                SensorManager.SENSOR_DELAY_GAME, sensorHandler);
        if (!sensorRegistered) {
            activeSensorListener = null;
            LimeLog.warning("Unable to register the rotation vector sensor for SBS head tracking");
        }
    }

    private void handleSensorChanged(SensorEvent event, int currentGeneration) {
        if (!isCurrentGeneration(currentGeneration)) {
            return;
        }

        SensorManager.getRotationMatrixFromVector(currentMatrix, event.values);
        if (!baselineValid) {
            System.arraycopy(currentMatrix, 0, baselineMatrix, 0, baselineMatrix.length);
            baselineValid = true;
            publishPose(Pose.CENTERED, currentGeneration);
            return;
        }

        computeOrientationDegrees(baselineMatrix, currentMatrix, displayRotation,
                orientationDegrees);
        publishPose(new Pose(orientationDegrees[0], orientationDegrees[1]), currentGeneration);
    }

    private boolean isCurrentGeneration(int currentGeneration) {
        return running && generation == currentGeneration;
    }

    private void publishPose(Pose newPose, int currentGeneration) {
        if (!isCurrentGeneration(currentGeneration)) {
            return;
        }
        pose.set(newPose);
        notifyPoseChanged(currentGeneration);
    }

    private void notifyPoseChanged(int currentGeneration) {
        Runnable listener = poseChangedListener;
        if (listener != null && isCurrentGeneration(currentGeneration)) {
            listener.run();
        }
    }

    static void computeOrientationDegrees(float[] baseline, float[] current,
                                          int displayRotation, float[] output) {
        if (baseline.length != 9 || current.length != 9 || output.length < 2) {
            throw new IllegalArgumentException("Invalid head tracking matrix size");
        }

        // Forward (+Z) of the current phone pose expressed in the centered phone frame.
        float forwardX = baseline[0] * current[2] + baseline[3] * current[5] +
                baseline[6] * current[8];
        float forwardY = baseline[1] * current[2] + baseline[4] * current[5] +
                baseline[7] * current[8];
        float forwardZ = baseline[2] * current[2] + baseline[5] * current[5] +
                baseline[8] * current[8];

        float screenX;
        float screenY;
        switch (displayRotation) {
            case Surface.ROTATION_90:
                screenX = forwardY;
                screenY = -forwardX;
                break;
            case Surface.ROTATION_180:
                screenX = -forwardX;
                screenY = -forwardY;
                break;
            case Surface.ROTATION_270:
                screenX = -forwardY;
                screenY = forwardX;
                break;
            case Surface.ROTATION_0:
            default:
                screenX = forwardX;
                screenY = forwardY;
                break;
        }

        output[0] = (float) Math.toDegrees(Math.atan2(screenX, forwardZ));
        output[1] = (float) Math.toDegrees(Math.atan2(-screenY,
                Math.hypot(screenX, forwardZ)));
    }
}
