package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.limelight.R;

public class ResolutionPreference extends DialogPreference {
    private RadioButton a4_3;
    private RadioButton a16_9;
    private RadioButton a16_10;
    private SeekBar resolutionSeekBar;
    private TextView resolutionText;

    private int resolutionX;
    private int resolutionY;

    public ResolutionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.dialog_resolution);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);

        setDialogIcon(null);
    }

    private int getPreferenceState() {
        int checkboxState;
        int sliderState;

        if (a4_3.isChecked()) {
            checkboxState = 1;
        }
        else if (a16_10.isChecked()) {
            checkboxState = 2;
        }
        else {
            checkboxState = 0;
        }

        sliderState = resolutionSeekBar.getProgress();

        return checkboxState << 24 | sliderState;
    }

    private void setPreferenceState(int state) {
        int checkboxState;
        int sliderState;

        checkboxState = state >> 24;
        sliderState = state & 0x00FFFFFF;

        a4_3.setChecked(false);
        a16_10.setChecked(false);
        a16_9.setChecked(false);

        if (checkboxState == 1) {
            a4_3.setChecked(true);
        }
        else if (checkboxState == 2) {
            a16_10.setChecked(true);
        }
        else {
            a16_9.setChecked(true);
        }

        resolutionSeekBar.setProgress(sliderState);
    }

    private void updateResolution() {
        double aspectFactor;
        int step = resolutionSeekBar.getProgress() + 1;

        if (a4_3.isChecked()) {
            aspectFactor = 4.0/3.0;

            switch (step) {
                case 1:
                    // 640x480
                    resolutionY = 480;
                    break;
                case 2:
                    // 800x600
                    resolutionY = 600;
                    break;
                case 3:
                    // 1024x768
                    resolutionY = 768;
                    break;
                case 4:
                    // 1600x1200
                    resolutionY = 1200;
                    break;
            }
        }
        else if (a16_9.isChecked()) {
            aspectFactor = 16.0/9.0;

            switch (step) {
                case 1:
                    // 1280x720
                    resolutionY = 720;
                    break;
                case 2:
                    // 1920x1080
                    resolutionY = 1080;
                    break;
                case 3:
                    // 2560x1440
                    resolutionY = 1440;
                    break;
                case 4:
                    // 3840x2160
                    resolutionY = 2160;
                    break;
            }
        }
        else /* if (a16_10.isChecked() */ {
            aspectFactor = 16.0/10.0;

            switch (step) {
                case 1:
                    // 1280x800
                    resolutionY = 800;
                    break;
                case 2:
                    // 1920x1200
                    resolutionY = 1200;
                    break;
                case 3:
                    // 2560x1600
                    resolutionY = 1600;
                    break;
                case 4:
                    // 3840x2400
                    resolutionY = 2400;
                    break;
            }
        }

        resolutionX = (int)(resolutionY * aspectFactor);

        resolutionText.setText(resolutionX+"x"+resolutionY);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            SharedPreferences.Editor e = getPreferenceManager().getSharedPreferences().edit();
            e.putInt(PreferenceConfiguration.RESX_PREF_STRING, resolutionX);
            e.putInt(PreferenceConfiguration.RESY_PREF_STRING, resolutionY);
            persistInt(getPreferenceState());
            e.commit();
        }
    }

    @Override
    protected View onCreateDialogView() {
        View v = super.onCreateDialogView();

        a4_3 = (RadioButton) v.findViewById(R.id.aspect_4_3);
        a16_9 = (RadioButton) v.findViewById(R.id.aspect_16_9);
        a16_10 = (RadioButton) v.findViewById(R.id.aspect_16_10);
        resolutionText = (TextView) v.findViewById(R.id.resolutionText);
        resolutionSeekBar = (SeekBar) v.findViewById(R.id.resolutionSeekbar);

        CompoundButton.OnCheckedChangeListener ccListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateResolution();
            }
        };
        a4_3.setOnCheckedChangeListener(ccListener);
        a16_9.setOnCheckedChangeListener(ccListener);
        a16_10.setOnCheckedChangeListener(ccListener);

        resolutionSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateResolution();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Check the correct checkbox
        setPreferenceState(getPersistedInt(0));

        // Set initial resolution value
        updateResolution();

        return v;
    }
}
