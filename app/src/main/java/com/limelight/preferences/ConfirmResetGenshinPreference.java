package com.limelight.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.binding.input.genshin.GenshinProfile;

public class ConfirmResetGenshinPreference extends DialogPreference {
    public ConfirmResetGenshinPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ConfirmResetGenshinPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ConfirmResetGenshinPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConfirmResetGenshinPreference(Context context) {
        super(context);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            GenshinProfile.clearAllProfiles(getContext());
            Toast.makeText(getContext(), R.string.toast_reset_genshin_success, Toast.LENGTH_SHORT).show();
        }
    }
}
