package com.limelight.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Activity;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.limelight.R;
import com.limelight.utils.UiHelper;

public class StreamSettings extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
	
	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
	String locale = prefs.getString("list_languages", "default");
	if (!locale.equals("default")) {
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
