package com.limelight.preferences;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.Display;

import com.limelight.LimeLog;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.utils.UiHelper;

public class StreamSettings extends Activity {
    private PreferenceConfiguration previousPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        previousPrefs = PreferenceConfiguration.readPreferences(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_stream_settings);
        getFragmentManager().beginTransaction().replace(
                R.id.stream_settings, new SettingsFragment()
        ).commit();

        UiHelper.notifyNewRootView(this);
    }

    @Override
    public void onBackPressed() {
        finish();

        // Check for changes that require a UI reload to take effect
        PreferenceConfiguration newPrefs = PreferenceConfiguration.readPreferences(this);
        if (newPrefs.listMode != previousPrefs.listMode ||
                newPrefs.smallIconMode != previousPrefs.smallIconMode ||
                !newPrefs.language.equals(previousPrefs.language)) {
            // Restart the PC view to apply UI changes
            Intent intent = new Intent(this, PcView.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent, null);
        }
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);
            PreferenceScreen screen = getPreferenceScreen();

            // hide on-screen controls category on non touch screen devices
            if (!getActivity().getPackageManager().
                    hasSystemFeature("android.hardware.touchscreen")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_onscreen_controls");
                screen.removePreference(category);
            }

            // Remove HDR preference for devices below Nougat
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                LimeLog.info("Excluding HDR toggle based on OS");
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_advanced_settings");
                category.removePreference(findPreference("checkbox_enable_hdr"));
            }
            else {
                Display display = getActivity().getWindowManager().getDefaultDisplay();
                Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();

                // We must now ensure our display is compatible with HDR10
                boolean foundHdr10 = false;
                for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                    if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                        foundHdr10 = true;
                    }
                }

                if (!foundHdr10) {
                    LimeLog.info("Excluding HDR toggle based on display capabilities");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_advanced_settings");
                    category.removePreference(findPreference("checkbox_enable_hdr"));
                }
            }

            // Add a listener to the FPS and resolution preference
            // so the bitrate can be auto-adjusted
            Preference pref = findPreference(PreferenceConfiguration.RES_FPS_PREF_STRING);
            pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    // Write the new bitrate value
                    prefs.edit()
                            .putInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                                    PreferenceConfiguration.getDefaultBitrate(valueStr))
                            .apply();

                    // Allow the original preference change to take place
                    return true;
                }
            });
        }
    }
}
