package com.limelight.preferences;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Range;
import android.view.Display;

import com.limelight.LimeLog;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.binding.video.MediaCodecHelper;
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

        private static void removeResolution(ListPreference pref, String prefix) {
            int matchingCount = 0;

            // Count the number of matching entries we'll be removing
            for (CharSequence seq : pref.getEntryValues()) {
                if (seq.toString().startsWith(prefix)) {
                    matchingCount++;
                }
            }

            // Create the new arrays
            CharSequence[] entries = new CharSequence[pref.getEntries().length-matchingCount];
            CharSequence[] entryValues = new CharSequence[pref.getEntryValues().length-matchingCount];
            int outIndex = 0;
            for (int i = 0; i < pref.getEntryValues().length; i++) {
                if (pref.getEntryValues()[i].toString().startsWith(prefix)) {
                    // Skip matching prefixes
                    continue;
                }

                entries[outIndex] = pref.getEntries()[i];
                entryValues[outIndex] = pref.getEntryValues()[i];
                outIndex++;
            }

            // Update the preference with the new list
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

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

            // Remove PiP mode on devices pre-Oreo
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_basic_settings");
                category.removePreference(findPreference("checkbox_enable_pip"));
            }

            // Hide non-supported resolution/FPS combinations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Display display = getActivity().getWindowManager().getDefaultDisplay();

                int maxSupportedResW = 0;

                // Always allow resolutions that are smaller or equal to the active
                // display resolution because decoders can report total non-sense to us.
                // For example, a p201 device reports:
                // AVC Decoder: OMX.amlogic.avc.decoder.awesome
                // HEVC Decoder: OMX.amlogic.hevc.decoder.awesome
                // AVC supported width range: 64 - 384
                // HEVC supported width range: 64 - 544
                for (Display.Mode candidate : display.getSupportedModes()) {
                    // Some devices report their dimensions in the portrait orientation
                    // where height > width. Normalize these to the conventional width > height
                    // arrangement before we process them.

                    int width = Math.max(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    int height = Math.min(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());

                    if ((width >= 3840 || height >= 2160) && maxSupportedResW < 3840) {
                        maxSupportedResW = 3840;
                    }
                    else if ((width >= 1920 || height >= 1080) && maxSupportedResW < 1920) {
                        maxSupportedResW = 1920;
                    }
                }

                // This must be called to do runtime initialization before calling functions that evaluate
                // decoder lists.
                MediaCodecHelper.initialize(getContext(), GlPreferences.readPreferences(getContext()).glRenderer);

                MediaCodecInfo avcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", -1);
                MediaCodecInfo hevcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1);

                if (avcDecoder != null) {
                    Range<Integer> avcWidthRange = avcDecoder.getCapabilitiesForType("video/avc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("AVC supported width range: "+avcWidthRange.getLower()+" - "+avcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (avcWidthRange.contains(1280)) {
                        if (avcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (avcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                if (hevcDecoder != null) {
                    Range<Integer> hevcWidthRange = hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("HEVC supported width range: "+hevcWidthRange.getLower()+" - "+hevcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (hevcWidthRange.contains(1280)) {
                        if (hevcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (hevcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                LimeLog.info("Maximum resolution slot: "+maxSupportedResW);

                ListPreference resPref = (ListPreference) findPreference("list_resolution_fps");
                if (maxSupportedResW != 0) {
                    if (maxSupportedResW < 3840) {
                        // 4K is unsupported
                        removeResolution(resPref, "4K");
                    }
                    if (maxSupportedResW < 1920) {
                        // 1080p is unsupported
                        removeResolution(resPref, "1080p");
                    }
                    // Never remove 720p
                }
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
