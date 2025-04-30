package com.limelight.preferences;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;

import static com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader.OSC_PREFERENCE;
import static com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;

public class ConfirmDeleteKeyboardPreference extends DialogPreference {

    public ConfirmDeleteKeyboardPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ConfirmDeleteKeyboardPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ConfirmDeleteKeyboardPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ConfirmDeleteKeyboardPreference(@NonNull Context context) {
        super(context);
    }

    public static class DialogFragmentCompat extends PreferenceDialogFragmentCompat {
        public static DialogFragmentCompat newInstance(String key) {
            final DialogFragmentCompat fragment = new DialogFragmentCompat();
            final Bundle bundle = new Bundle(1);
            bundle.putString(ARG_KEY, key);
            fragment.setArguments(bundle);
            return fragment;
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            if (positiveResult) {
                String name= PreferenceManager.getDefaultSharedPreferences(getContext()).getString(OSC_PREFERENCE,OSC_PREFERENCE_VALUE);
                getContext().getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply();
                Toast.makeText(getContext(), R.string.toast_reset_osc_success, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
