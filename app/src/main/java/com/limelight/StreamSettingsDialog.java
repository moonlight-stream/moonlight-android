package com.limelight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.input.touch.AbsoluteTouchContext;
import com.limelight.binding.input.touch.RelativeTouchContext;
import com.limelight.binding.input.touch.TouchContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.StreamView;

public class StreamSettingsDialog {
    private static final int REFERENCE_HORIZ_RES = 1280;
    private static final int REFERENCE_VERT_RES = 720;

    private Activity activity;
    private PreferenceConfiguration prefConfig;
    private TextView performanceOverlayView;
    private TouchContext[] touchContextMap;
    private NvConnection conn;
    private StreamView streamView;
    private OnQuitRequestedListener quitListener;
    
    private AlertDialog dialog;

    public interface OnQuitRequestedListener {
        void onQuitRequested();
    }

    public StreamSettingsDialog(Activity activity, PreferenceConfiguration prefConfig,
                               TextView performanceOverlayView,
                               TouchContext[] touchContextMap, NvConnection conn,
                               StreamView streamView, OnQuitRequestedListener quitListener) {
        this.activity = activity;
        this.prefConfig = prefConfig;
        this.performanceOverlayView = performanceOverlayView;
        this.touchContextMap = touchContextMap;
        this.conn = conn;
        this.streamView = streamView;
        this.quitListener = quitListener;
    }

    public void show() {
        // Don't show dialog if already showing
        if (dialog != null && dialog.isShowing()) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Inflate the custom layout
                LayoutInflater inflater = LayoutInflater.from(activity);
                View dialogView = inflater.inflate(R.layout.dialog_stream_settings, null);

                // Get current values
                int currentWidth = prefConfig.width;
                int currentHeight = prefConfig.height;
                int currentFps = prefConfig.fps;
                boolean currentPerfOverlay = prefConfig.enablePerfOverlay;

                AudioManager audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
                int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int volumePercent = (int) ((currentVolume / (float) maxVolume) * 100);

                // Initialize resolution radio buttons
                RadioGroup resolutionGroup = dialogView.findViewById(R.id.resolutionGroup);
                RadioButton resolution720p = dialogView.findViewById(R.id.resolution720p);
                RadioButton resolution1080p = dialogView.findViewById(R.id.resolution1080p);
                RadioButton resolution2k = dialogView.findViewById(R.id.resolution2k);
                RadioButton resolution4k = dialogView.findViewById(R.id.resolution4k);

                // Set current resolution selection
                if (currentWidth == 1280 && currentHeight == 720) {
                    resolution720p.setChecked(true);
                } else if (currentWidth == 1920 && currentHeight == 1080) {
                    resolution1080p.setChecked(true);
                } else if (currentWidth == 2560 && currentHeight == 1440) {
                    resolution2k.setChecked(true);
                } else if (currentWidth == 3840 && currentHeight == 2160) {
                    resolution4k.setChecked(true);
                } else {
                    // Default to 720p if unknown resolution
                    resolution720p.setChecked(true);
                }

                // Update radio button backgrounds based on selection
                updateResolutionButtonBackground(resolution720p, resolution720p.isChecked());
                updateResolutionButtonBackground(resolution1080p, resolution1080p.isChecked());
                updateResolutionButtonBackground(resolution2k, resolution2k.isChecked());
                updateResolutionButtonBackground(resolution4k, resolution4k.isChecked());

                // Also update visuals when focus changes so focused item stands out for gamepad users
                View.OnFocusChangeListener resolutionFocusListener = new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        updateResolutionButtonBackground(resolution720p, resolution720p.isChecked());
                        updateResolutionButtonBackground(resolution1080p, resolution1080p.isChecked());
                        updateResolutionButtonBackground(resolution2k, resolution2k.isChecked());
                        updateResolutionButtonBackground(resolution4k, resolution4k.isChecked());
                    }
                };
                resolution720p.setOnFocusChangeListener(resolutionFocusListener);
                resolution1080p.setOnFocusChangeListener(resolutionFocusListener);
                resolution2k.setOnFocusChangeListener(resolutionFocusListener);
                resolution4k.setOnFocusChangeListener(resolutionFocusListener);

                // Track previous selection to allow reverting on cancel
                final int[] previousCheckedId = {resolutionGroup.getCheckedRadioButtonId()};
                final boolean[] ignoreResolutionChange = {false};

                // Listen for resolution changes and ask to quit to take effect
                resolutionGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup group, int checkedId) {
                        if (ignoreResolutionChange[0]) {
                            return;
                        }

                        // Ignore if nothing actually changed
                        if (checkedId == previousCheckedId[0]) {
                            return;
                        }

                        int selectedResolutionId = checkedId;
                        int newWidth = 1280;
                        int newHeight = 720;
                        String resolutionString = PreferenceConfiguration.RES_720P;

                        if (selectedResolutionId == R.id.resolution1080p) {
                            newWidth = 1920;
                            newHeight = 1080;
                            resolutionString = PreferenceConfiguration.RES_1080P;
                        } else if (selectedResolutionId == R.id.resolution2k) {
                            newWidth = 2560;
                            newHeight = 1440;
                            resolutionString = PreferenceConfiguration.RES_1440P;
                        } else if (selectedResolutionId == R.id.resolution4k) {
                            newWidth = 3840;
                            newHeight = 2160;
                            resolutionString = PreferenceConfiguration.RES_4K;
                        }

                        // Update radio button backgrounds for new selection
                        updateResolutionButtonBackground(resolution720p, checkedId == R.id.resolution720p);
                        updateResolutionButtonBackground(resolution1080p, checkedId == R.id.resolution1080p);
                        updateResolutionButtonBackground(resolution2k, checkedId == R.id.resolution2k);
                        updateResolutionButtonBackground(resolution4k, checkedId == R.id.resolution4k);

                        // If resolution didn't change, nothing else to do
                        if (newWidth == currentWidth && newHeight == currentHeight) {
                            previousCheckedId[0] = checkedId;
                            return;
                        }

                        // Make resolution string effectively final for inner classes
                        final String finalResolutionString = resolutionString;

                        // Ask user whether to quit for the change to take effect
                        new AlertDialog.Builder(activity)
                                .setTitle("分辨率更改")
                                .setMessage("需要退出应用以使更改生效。是否现在退出？")
                                .setPositiveButton("是", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Save resolution preference and quit
                                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                                        prefs.edit()
                                                .putString("list_resolution", finalResolutionString)
                                                .apply();

                                        previousCheckedId[0] = checkedId;
                                        if (quitListener != null) {
                                            quitListener.onQuitRequested();
                                        }
                                    }
                                })
                                .setNegativeButton("否", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Revert selection to previous without retriggering listener
                                        ignoreResolutionChange[0] = true;
                                        resolutionGroup.check(previousCheckedId[0]);
                                        updateResolutionButtonBackground(resolution720p, previousCheckedId[0] == R.id.resolution720p);
                                        updateResolutionButtonBackground(resolution1080p, previousCheckedId[0] == R.id.resolution1080p);
                                        updateResolutionButtonBackground(resolution2k, previousCheckedId[0] == R.id.resolution2k);
                                        updateResolutionButtonBackground(resolution4k, previousCheckedId[0] == R.id.resolution4k);
                                        ignoreResolutionChange[0] = false;
                                    }
                                })
                                .setCancelable(true)
                                .show();
                    }
                });

                // Initialize FPS radio buttons
                RadioGroup fpsGroup = dialogView.findViewById(R.id.fpsGroup);
                RadioButton fps30 = dialogView.findViewById(R.id.fps30);
                RadioButton fps60 = dialogView.findViewById(R.id.fps60);
                RadioButton fps90 = dialogView.findViewById(R.id.fps90);
                RadioButton fps120 = dialogView.findViewById(R.id.fps120);

                // Set current FPS selection
                if (currentFps == 30) {
                    fps30.setChecked(true);
                } else if (currentFps == 60) {
                    fps60.setChecked(true);
                } else if (currentFps == 90) {
                    fps90.setChecked(true);
                } else if (currentFps == 120) {
                    fps120.setChecked(true);
                } else {
                    // Default to 60 if unknown FPS
                    fps60.setChecked(true);
                }

                // Update FPS radio button backgrounds based on selection
                updateResolutionButtonBackground(fps30, fps30.isChecked());
                updateResolutionButtonBackground(fps60, fps60.isChecked());
                updateResolutionButtonBackground(fps90, fps90.isChecked());
                updateResolutionButtonBackground(fps120, fps120.isChecked());

                // Also update visuals when focus changes
                View.OnFocusChangeListener fpsFocusListener = new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        updateResolutionButtonBackground(fps30, fps30.isChecked());
                        updateResolutionButtonBackground(fps60, fps60.isChecked());
                        updateResolutionButtonBackground(fps90, fps90.isChecked());
                        updateResolutionButtonBackground(fps120, fps120.isChecked());
                    }
                };
                fps30.setOnFocusChangeListener(fpsFocusListener);
                fps60.setOnFocusChangeListener(fpsFocusListener);
                fps90.setOnFocusChangeListener(fpsFocusListener);
                fps120.setOnFocusChangeListener(fpsFocusListener);

                // Track previous FPS selection to allow reverting on cancel
                final int[] previousFpsCheckedId = {fpsGroup.getCheckedRadioButtonId()};
                final boolean[] ignoreFpsChange = {false};

                // Listen for FPS changes and ask to quit to take effect
                fpsGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup group, int checkedId) {
                        if (ignoreFpsChange[0]) {
                            return;
                        }

                        // Ignore if nothing actually changed
                        if (checkedId == previousFpsCheckedId[0]) {
                            return;
                        }

                        int selectedFpsId = checkedId;
                        int newFps = 60;
                        String fpsString = "60";

                        if (selectedFpsId == R.id.fps30) {
                            newFps = 30;
                            fpsString = "30";
                        } else if (selectedFpsId == R.id.fps60) {
                            newFps = 60;
                            fpsString = "60";
                        } else if (selectedFpsId == R.id.fps90) {
                            newFps = 90;
                            fpsString = "90";
                        } else if (selectedFpsId == R.id.fps120) {
                            newFps = 120;
                            fpsString = "120";
                        }

                        // Update FPS radio button backgrounds for new selection
                        updateResolutionButtonBackground(fps30, checkedId == R.id.fps30);
                        updateResolutionButtonBackground(fps60, checkedId == R.id.fps60);
                        updateResolutionButtonBackground(fps90, checkedId == R.id.fps90);
                        updateResolutionButtonBackground(fps120, checkedId == R.id.fps120);

                        // If FPS didn't change, nothing else to do
                        if (newFps == currentFps) {
                            previousFpsCheckedId[0] = checkedId;
                            return;
                        }

                        // Make FPS string effectively final for inner classes
                        final String finalFpsString = fpsString;

                        // Ask user whether to quit for the change to take effect
                        new AlertDialog.Builder(activity)
                                .setTitle("帧率更改")
                                .setMessage("需要退出应用以使更改生效。是否现在退出？")
                                .setPositiveButton("是", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Save FPS preference
                                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                                        prefs.edit()
                                                .putString("list_fps", finalFpsString)
                                                .apply();

                                        // Quit application
                                        if (quitListener != null) {
                                            quitListener.onQuitRequested();
                                        }
                                    }
                                })
                                .setNegativeButton("否", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Revert selection to previous without retriggering listener
                                        ignoreFpsChange[0] = true;
                                        fpsGroup.check(previousFpsCheckedId[0]);
                                        updateResolutionButtonBackground(fps30, previousFpsCheckedId[0] == R.id.fps30);
                                        updateResolutionButtonBackground(fps60, previousFpsCheckedId[0] == R.id.fps60);
                                        updateResolutionButtonBackground(fps90, previousFpsCheckedId[0] == R.id.fps90);
                                        updateResolutionButtonBackground(fps120, previousFpsCheckedId[0] == R.id.fps120);
                                        ignoreFpsChange[0] = false;
                                    }
                                })
                                .setCancelable(true)
                                .show();
                    }
                });

                // Initialize stream info switch
                CheckBox streamInfoSwitch = dialogView.findViewById(R.id.streamInfoSwitch);
                streamInfoSwitch.setChecked(currentPerfOverlay);

                // Initialize on-screen controls visibility switch
                CheckBox showOscSwitch = dialogView.findViewById(R.id.showOscSwitch);
                showOscSwitch.setChecked(prefConfig.onscreenController);

                // Initialize touchscreen-as-touchpad switch
                CheckBox touchscreenTrackpadSwitch = dialogView.findViewById(R.id.touchscreenTrackpadSwitch);
                touchscreenTrackpadSwitch.setChecked(prefConfig.touchscreenTrackpad);

                // Listen for performance overlay toggle to take effect immediately
                streamInfoSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        // Persist setting
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                        prefs.edit().putBoolean("checkbox_enable_perf_overlay", isChecked).apply();

                        // Update in-memory config and UI immediately
                        prefConfig.enablePerfOverlay = isChecked;
                        if (isChecked) {
                            performanceOverlayView.setVisibility(View.VISIBLE);
                        } else {
                            performanceOverlayView.setVisibility(View.GONE);
                        }
                    }
                });

                // Listen for on-screen controls visibility toggle
                showOscSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                        prefs.edit().putBoolean("checkbox_show_onscreen_controls", isChecked).apply();

                        prefConfig.onscreenController = isChecked;

                        if (isChecked) {
                            ((Game) activity).showVirtualController();
                        } else {
                            ((Game) activity).hideVirtualController();
                        }
                    }
                });

                // Listen for touchscreen-as-touchpad toggle (take effect immediately)
                touchscreenTrackpadSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        // Persist setting
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                        prefs.edit().putBoolean("checkbox_touchscreen_trackpad", isChecked).apply();

                        // Update in-memory config
                        prefConfig.touchscreenTrackpad = isChecked;

                        // Reinitialize touch contexts to use the new mode immediately
                        for (int i = 0; i < touchContextMap.length; i++) {
                            if (!prefConfig.touchscreenTrackpad) {
                                touchContextMap[i] = new AbsoluteTouchContext(conn, i, streamView);
                            } else {
                                touchContextMap[i] = new RelativeTouchContext(conn, i,
                                        REFERENCE_HORIZ_RES, REFERENCE_VERT_RES,
                                        streamView, prefConfig);
                            }
                        }

                        Toast.makeText(activity,
                                "触控板模式已更新。",
                                Toast.LENGTH_SHORT).show();
                    }
                });

                // Initialize volume seekbar
                SeekBar volumeSeekBar = dialogView.findViewById(R.id.volumeSeekBar);
                volumeSeekBar.setProgress(volumePercent);
                // Update stream volume immediately as the user adjusts the slider
                volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (!fromUser) {
                            return;
                        }
                        int newVolume = (int) ((progress / 100.0f) * maxVolume);
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // No-op
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        // No-op
                    }
                });

                // Get buttons
                Button btnSaveContinue = dialogView.findViewById(R.id.btnSaveContinue);
                Button btnSaveExit = dialogView.findViewById(R.id.btnSaveExit);

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setView(dialogView)
                        .setCancelable(true)
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                StreamSettingsDialog.this.dialog = null;
                            }
                        });

                dialog = builder.create();
                // Ensure no system title bar is shown
                if (dialog.getWindow() != null) {
                    dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
                }

                // Handle Save & Continue button
                btnSaveContinue.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dismiss();
                    }
                });

                // Handle Save & Exit button (save settings then exit)
                btnSaveExit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (quitListener != null) {
                            quitListener.onQuitRequested();
                        }
                    }
                });

                // Visually distinguish focused buttons for gamepad users
                View.OnFocusChangeListener buttonFocusListener = new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (v instanceof Button) {
                            Button b = (Button) v;
                            if (hasFocus) {
                                b.setBackgroundColor(0xFF444444); // Dark gray when focused
                            } else {
                                b.setBackgroundColor(0xFF000000); // Black when not focused
                            }
                        }
                    }
                };
                btnSaveContinue.setOnFocusChangeListener(buttonFocusListener);
                btnSaveExit.setOnFocusChangeListener(buttonFocusListener);

                dialog.show();

                // Give initial focus to the primary button for gamepad navigation
                btnSaveContinue.requestFocus();
            }
        });
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            dialog = null;
        }
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    private void updateResolutionButtonBackground(RadioButton button, boolean isSelected) {
        boolean isFocused = button.isFocused();

        if (isSelected) {
            // Selected: blue background, brighter when focused
            button.setBackgroundColor(isFocused ? 0xFF42A5F5 : 0xFF2196F3);
            button.setTextColor(0xFFFFFFFF); // White text
        } else {
            // Not selected: dark background, lighter when focused
            button.setBackgroundColor(isFocused ? 0xFF444444 : 0xFF000000);
            button.setTextColor(0xFFFFFFFF); // White text
        }
    }
}

