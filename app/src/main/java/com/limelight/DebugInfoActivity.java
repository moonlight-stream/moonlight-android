package com.limelight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.limelight.utils.DeviceUtils;

import java.util.ArrayList;
import java.util.List;

public class DebugInfoActivity extends Activity implements View.OnClickListener {

    private TextView tx_gamepad_info;
    private Vibrator vibrator;
    private Button bt_vibrator;
    private List<InputDevice> ids = new ArrayList<>();
    private Vibrator vibratorOnline;
    private Button bt_vibrator_value;
    private int simulatedAmplitude = 220;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_axitest);

        tx_gamepad_info = findViewById(R.id.tx_game_pad_info);
        TextView tx_content = findViewById(R.id.tx_content);
        bt_vibrator = findViewById(R.id.bt_vibrator);
        bt_vibrator_value = findViewById(R.id.bt_vibrator_value);

        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String kernelVersion = System.getProperty("os.version");
        StringBuffer sb = new StringBuffer();
        sb.append(getString(R.string.debug_info_android_version) + DeviceUtils.getSDKVersionName());
        sb.append("\t" + getString(R.string.debug_info_api_version) + Build.VERSION.SDK_INT);
        sb.append("\n" + getString(R.string.debug_info_kernel_version) + kernelVersion);
        sb.append("\n" + getString(R.string.debug_info_brand_model) + DeviceUtils.getManufacturer() + "\t-\t" + DeviceUtils.getModel());
        tx_content.setText(sb.toString());

        boolean hasVibrator = ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator();
        String content = hasVibrator ? getString(R.string.debug_info_has_vibration_motor) : getString(R.string.debug_info_no_vibration_motor);
        bt_vibrator.setText(getString(R.string.debug_info_test_device_vibration, content));

        showSimlateAmp();
    }

    private void showSimlateAmp() {
        bt_vibrator_value.setText(getString(R.string.debug_info_vibration_amplitude, simulatedAmplitude));
    }

    private void cancleRumble() {
        if (vibratorOnline != null) {
            vibratorOnline.cancel();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.bt_vibrator_cancle) {
            cancleRumble();
            return;
        }
        // Device Vibration
        if (v.getId() == R.id.bt_vibrator) {
            String[] titles = new String[]{getString(R.string.debug_info_simple_vibration), getString(R.string.debug_info_continuous_hd_vibration)};
            new AlertDialog.Builder(this).setItems(titles, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    switch (which) {
                        case 0:
                            vibrator.vibrate(1000);
                            break;
                        case 1:
                            rumble(vibrator);
                            break;
                    }
                }
            }).setTitle(getString(R.string.debug_info_please_choose)).create().show();
            return;
        }

        // Gamepad Vibration
        if (v.getId() == R.id.bt_vibrator_gamepad) {
            if (ids.isEmpty()) {
                Toast.makeText(DebugInfoActivity.this, getString(R.string.debug_info_no_gamepad_detected), Toast.LENGTH_LONG).show();
                return;
            }
            String[] strings = new String[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                strings[i] = ids.get(i).getName();
            }
            new AlertDialog.Builder(this).setItems(strings, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    if (ids.get(which).getVibrator().hasVibrator()) {
                        String[] titles = new String[]{getString(R.string.debug_info_simple_vibration), getString(R.string.debug_info_continuous_hd_vibration)};
                        new AlertDialog.Builder(DebugInfoActivity.this).setItems(titles, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which2) {
                                dialog.dismiss();
                                switch (which2) {
                                    case 0:
                                        ids.get(which).getVibrator().vibrate(1000);
                                        break;
                                    case 1:
                                        cancleRumble();
                                        vibratorOnline = ids.get(which).getVibrator();
                                        rumble(vibratorOnline);
                                        break;
                                }
                            }
                        }).setTitle(getString(R.string.debug_info_please_choose)).create().show();
                    } else {
                        Toast.makeText(DebugInfoActivity.this, getString(R.string.debug_info_no_vibrator), Toast.LENGTH_SHORT).show();
                    }
                }
            }).setTitle(getString(R.string.debug_info_please_choose)).create().show();
            return;
        }

        // Refresh Gamepad Info
        if (v.getId() == R.id.bt_update_gamepad) {
            updateGamePad();
            return;
        }

        if (v.getId() == R.id.bt_vibrator_value) {
            SeekBar mSeekBar = getSeekBar();
            AlertDialog.Builder editDialog = new AlertDialog.Builder(this);
            editDialog.setTitle(getString(R.string.debug_info_set_amplitude));
            editDialog.setView(mSeekBar);
            editDialog.create().show();
        }
    }

    private SeekBar getSeekBar() {
        SeekBar mSeekBar = new SeekBar(this);
        mSeekBar.setMax(255);
        mSeekBar.setProgress(simulatedAmplitude);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                simulatedAmplitude = progress;
                showSimlateAmp();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        return mSeekBar;
    }

    private void rumble(Vibrator vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{1000}, new int[]{simulatedAmplitude}, 0));
        } else {
            long pwmPeriod = 20;
            long onTime = (long) ((simulatedAmplitude / 255.0) * pwmPeriod);
            long offTime = pwmPeriod - onTime;
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build();
            vibrator.vibrate(new long[]{0, onTime, offTime}, 0, audioAttributes);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vibratorOnline != null) {
            vibratorOnline.cancel();
        }
    }

    private void updateGamePad() {
        ids.clear();
        StringBuffer sb = new StringBuffer();
        sb.append("\n");
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice dev = InputDevice.getDevice(deviceId);
            int sources = dev.getSources();
            if (((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                    || ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {
                if (getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_X) != null &&
                        getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Y) != null) {
                    // This is a gamepad
                    ids.add(dev);
                    sb.append(getString(R.string.debug_info_name) + dev.getName());
                    sb.append("\n");
                    sb.append(getString(R.string.debug_info_sensors));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        String sensor = "";
                        if (dev.getSensorManager().getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                            sensor += getString(R.string.debug_info_accelerometer);
                        }
                        if (dev.getSensorManager().getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
                            sensor += getString(R.string.debug_info_gyroscope);
                        }
                        if (sensor.length() == 0) {
                            sb.append(getString(R.string.debug_info_no_relevant_driver));
                        } else {
                            sb.append(sensor);
                        }
                        sb.append("\n");
                    } else {
                        sb.append(getString(R.string.debug_info_no_api_below_android12));
                        sb.append("\n");
                    }
                    sb.append(getString(R.string.debug_info_vid_pid) + dev.getVendorId() + "_" + dev.getProductId()
                            + "\t    [" + String.format("%04x", dev.getVendorId()) + "_" + String.format("%04x", dev.getProductId()) + "]");
                    sb.append("\n");
                    sb.append(getString(R.string.debug_info_vibration) + (dev.getVibrator().hasVibrator() ? getString(R.string.debug_info_supported) : getString(R.string.debug_info_not_supported)));
                    sb.append("\n");
                    sb.append(getString(R.string.debug_info_details) + "\n");
                    sb.append(dev.toString());
                    sb.append("\n");
                }
            }
        }
        tx_gamepad_info.setText(getString(R.string.debug_info_number_of_gamepads) + ids.size() + "\n" + sb.toString());
    }

    private static InputDevice.MotionRange getMotionRangeForJoystickAxis(InputDevice dev, int axis) {
        InputDevice.MotionRange range;

        // First get the axis for SOURCE_JOYSTICK
        range = dev.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK);
        if (range == null) {
            // Now try the axis for SOURCE_GAMEPAD
            range = dev.getMotionRange(axis, InputDevice.SOURCE_GAMEPAD);
        }

        return range;
    }
}
