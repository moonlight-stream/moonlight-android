package com.limelight.preferences;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.Toast;

import com.limelight.R;

import static com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader.OSC_PREFERENCE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

public class ConfirmDeleteOscPreference extends DialogPreference {
    public ConfirmDeleteOscPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ConfirmDeleteOscPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ConfirmDeleteOscPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ConfirmDeleteOscPreference(@NonNull Context context) {
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
                getContext().getSharedPreferences(OSC_PREFERENCE, Context.MODE_PRIVATE).edit().clear().apply();
                Toast.makeText(getContext(), R.string.toast_reset_osc_success, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
