package com.limelight.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;

public class SmallIconCheckboxPreference extends CheckBoxPreference {
    public SmallIconCheckboxPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SmallIconCheckboxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return PreferenceConfiguration.getDefaultSmallMode(getContext());
    }
}
