package com.limelight.preferences;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.limelight.utils.UiHelper;

public class LanguagePreference extends ListPreference {

    public LanguagePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LanguagePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LanguagePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LanguagePreference(@NonNull Context context) {
        super(context);
    }

//    @Override
//    protected void onClick() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            try {
//                // Launch the Android native app locale settings page
//                Intent intent = new Intent(Settings.ACTION_APP_LOCALE_SETTINGS);
//                intent.addCategory(Intent.CATEGORY_DEFAULT);
//                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
//                getContext().startActivity(intent, null);
//                return;
//            } catch (ActivityNotFoundException e) {
//                // App locale settings should be present on all Android 13 devices,
//                // but if not, we'll launch the old language chooser.
//            }
//        }
//
//        // If we don't have native app locale settings, launch the normal dialog
//        super.onClick();
//    }
}
