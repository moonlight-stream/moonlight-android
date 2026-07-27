package com.limelight.preferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.binding.input.RearButtonProfile;
import com.limelight.binding.input.RearButtonProfileStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures up to four physical rear-button events and associates them with a primary gamepad.
 */
public final class RearButtonCalibration extends Activity {
    private static final int MIN_BUTTON_COUNT = 1;
    private static final int MAX_BUTTON_COUNT = 4;
    private static final int DEFAULT_BUTTON_COUNT = 2;

    private final List<InputDevice> targetDevices = new ArrayList<>();
    private final List<RearButtonProfile.ButtonBinding> capturedBindings = new ArrayList<>();

    private RearButtonProfileStore profileStore;
    private Spinner targetSpinner;
    private TextView statusText;
    private Button saveButton;
    private int targetButtonCount = DEFAULT_BUTTON_COUNT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profileStore = new RearButtonProfileStore(this);
        setTitle(R.string.title_rear_button_calibration);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView instructions = new TextView(this);
        instructions.setText(R.string.rear_button_calibration_instructions);
        instructions.setTextSize(18);
        root.addView(instructions);

        targetSpinner = new Spinner(this);
        root.addView(targetSpinner);
        populateTargetDevices();

        TextView buttonCountLabel = new TextView(this);
        buttonCountLabel.setText(R.string.rear_button_count_label);
        buttonCountLabel.setPadding(0, 32, 0, 0);
        root.addView(buttonCountLabel);

        Spinner buttonCountSpinner = new Spinner(this);
        List<String> buttonCounts = new ArrayList<>();
        for (int count = MIN_BUTTON_COUNT; count <= MAX_BUTTON_COUNT; count++) {
            buttonCounts.add(Integer.toString(count));
        }
        buttonCountSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, buttonCounts));
        buttonCountSpinner.setSelection(DEFAULT_BUTTON_COUNT - MIN_BUTTON_COUNT);
        root.addView(buttonCountSpinner);

        statusText = new TextView(this);
        statusText.setPadding(0, 32, 0, 32);
        statusText.setGravity(Gravity.CENTER_HORIZONTAL);
        statusText.setTextSize(20);
        root.addView(statusText);

        saveButton = new Button(this);
        saveButton.setText(R.string.action_save_rear_buttons);
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(view -> saveProfile());
        root.addView(saveButton);

        Button restartButton = new Button(this);
        restartButton.setText(R.string.action_restart_rear_button_capture);
        restartButton.setOnClickListener(view -> {
            capturedBindings.clear();
            updateStatus();
        });
        root.addView(restartButton);

        Button clearButton = new Button(this);
        clearButton.setText(R.string.action_clear_rear_button_profiles);
        clearButton.setOnClickListener(view -> confirmClearProfiles());
        root.addView(clearButton);

        Button cancelButton = new Button(this);
        cancelButton.setText(android.R.string.cancel);
        cancelButton.setOnClickListener(view -> finish());
        root.addView(cancelButton);

        buttonCountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedCount = position + MIN_BUTTON_COUNT;
                if (selectedCount != targetButtonCount) {
                    targetButtonCount = selectedCount;
                    capturedBindings.clear();
                    updateStatus();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Keep the previous valid count.
            }
        });

        setContentView(root);
        updateStatus();
    }

    /**
     * Captures key-down events before the focused view can consume unknown handheld buttons.
     *
     * @param event dispatched key event
     * @return whether the event was consumed
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 &&
                capturedBindings.size() < targetButtonCount && captureBinding(event)) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP &&
                capturedBindings.size() <= targetButtonCount) {
            for (RearButtonProfile.ButtonBinding binding : capturedBindings) {
                InputDevice device = event.getDevice();
                if (device != null && binding.matches(
                        device.getDescriptor(), event.getKeyCode(), event.getScanCode())) {
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Populates the primary-controller selector from connected joystick and gamepad devices.
     */
    private void populateTargetDevices() {
        List<String> targetNames = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) {
                continue;
            }
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                targetDevices.add(device);
                targetNames.add(device.getName());
            }
        }

        if (targetNames.isEmpty()) {
            targetNames.add(getString(R.string.rear_button_no_gamepads));
        }
        targetSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, targetNames));
    }

    /**
     * Converts a dispatched key event into a unique profile binding.
     *
     * @param event key-down event to capture
     * @return {@code true} when the event is a usable or duplicate hardware event
     */
    private boolean captureBinding(KeyEvent event) {
        InputDevice source = event.getDevice();
        if (source == null || event.getKeyCode() == KeyEvent.KEYCODE_HOME ||
                event.getKeyCode() == KeyEvent.KEYCODE_POWER ||
                event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP ||
                event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false;
        }

        RearButtonProfile.ButtonBinding candidate = new RearButtonProfile.ButtonBinding(
                source.getDescriptor(), source.getName(), event.getKeyCode(), event.getScanCode());
        for (RearButtonProfile.ButtonBinding existing : capturedBindings) {
            if (existing.identity().equals(candidate.identity())) {
                Toast.makeText(this, R.string.rear_button_duplicate, Toast.LENGTH_SHORT).show();
                return true;
            }
        }

        capturedBindings.add(candidate);
        updateStatus();
        return true;
    }

    /**
     * Refreshes capture progress and save availability.
     */
    private void updateStatus() {
        if (capturedBindings.size() < targetButtonCount) {
            statusText.setText(getString(
                    R.string.rear_button_capture_progress,
                    capturedBindings.size() + 1,
                    targetButtonCount,
                    capturedBindings.size()));
        } else {
            statusText.setText(getString(
                    R.string.rear_button_capture_complete,
                    targetButtonCount));
        }
        saveButton.setEnabled(
                !targetDevices.isEmpty() && capturedBindings.size() == targetButtonCount);
    }

    /**
     * Saves the captured mapping for the selected target gamepad.
     */
    private void saveProfile() {
        int selectedIndex = targetSpinner.getSelectedItemPosition();
        if (selectedIndex < 0 || selectedIndex >= targetDevices.size() ||
                capturedBindings.isEmpty()) {
            return;
        }

        InputDevice target = targetDevices.get(selectedIndex);
        RearButtonProfile profile = new RearButtonProfile(
                target.getDescriptor(), target.getName(), capturedBindings);
        if (profileStore.saveProfile(profile)) {
            Toast.makeText(this, R.string.rear_button_profile_saved, Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, R.string.rear_button_profile_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Confirms removal of all stored profiles.
     */
    private void confirmClearProfiles() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_clear_rear_button_profiles)
                .setMessage(R.string.rear_button_clear_confirmation)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    profileStore.clearProfiles();
                    Toast.makeText(this, R.string.rear_button_profiles_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
