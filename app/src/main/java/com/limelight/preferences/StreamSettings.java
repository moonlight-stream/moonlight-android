package com.limelight.preferences;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.app.Activity;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.utils.UiHelper;

import java.util.Locale;

public class StreamSettings extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String locale = PreferenceConfiguration.readPreferences(this).language;
        if (!locale.equals(PreferenceConfiguration.DEFAULT_LANGUAGE)) {
            Configuration config = new Configuration(getResources().getConfiguration());
            config.locale = new Locale(locale);
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        }

        setContentView(R.layout.activity_stream_settings);
        getFragmentManager().beginTransaction().replace(
                R.id.stream_settings, new SettingsFragment()
        ).commit();

        UiHelper.notifyNewRootView(this);
    }

    @Override
    public void onBackPressed() {
        finish();

        // Restart the PC view to apply UI changes
        Intent intent = new Intent(this, PcView.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent, null);
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);

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
