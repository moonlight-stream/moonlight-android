package com.limelight.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Range;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.limelight.LimeLog;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StreamSettings extends Activity {
    private PreferenceConfiguration previousPrefs;

    void reloadSettings() {
        getFragmentManager().beginTransaction().replace(
                R.id.stream_settings, new SettingsFragment()
        ).commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        previousPrefs = PreferenceConfiguration.readPreferences(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_stream_settings);
        reloadSettings();

        UiHelper.notifyNewRootView(this);
    }

    @Override
    public void onBackPressed() {
        finish();

        // Check for changes that require a UI reload to take effect
        PreferenceConfiguration newPrefs = PreferenceConfiguration.readPreferences(this);
        if (!newPrefs.language.equals(previousPrefs.language)) {
            // Restart the PC view to apply UI changes
            Intent intent = new Intent(this, PcView.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent, null);
        }
    }

    public static class SettingsFragment extends PreferenceFragment {
        private boolean nativeResolutionOptionPresent = false;

        private void setValue(String preferenceKey, String value) {
            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            pref.setValue(value);
        }

        private void updateNativeResolutionEntry(int nativeWidth, int nativeHeight) {
            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING);

            String nameSuffix = " ("+nativeWidth+"x"+nativeHeight+")";
            String newValue = nativeWidth+"x"+nativeHeight;

            CharSequence[] entries = pref.getEntries();
            CharSequence[] values = pref.getEntryValues();

            // Check if the native resolution is already present
//            for (CharSequence value : values) {
//                if (newValue.equals(value.toString())) {
//                    // It is present in the default list, so remove the native option
//                    nativeResolutionOptionPresent = false;
//
//                    pref.setEntries(Arrays.copyOf(entries, entries.length - 1));
//                    pref.setEntryValues(Arrays.copyOf(values, values.length - 1));
//
//                    return;
//                }
//            }

            // Add the name suffix to the native option
            entries[entries.length - 1] = entries[entries.length - 1].toString() + nameSuffix;
            values[values.length - 1] = newValue;

            pref.setEntries(entries);
            pref.setEntryValues(values);

            nativeResolutionOptionPresent = true;
        }

        private void removeValue(String preferenceKey, String value, Runnable onMatched) {
            int matchingCount = 0;

            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            // Count the number of matching entries we'll be removing
            for (CharSequence seq : pref.getEntryValues()) {
                if (seq.toString().equalsIgnoreCase(value)) {
                    matchingCount++;
                }
            }

            // Create the new arrays
            CharSequence[] entries = new CharSequence[pref.getEntries().length-matchingCount];
            CharSequence[] entryValues = new CharSequence[pref.getEntryValues().length-matchingCount];
            int outIndex = 0;
            for (int i = 0; i < pref.getEntryValues().length; i++) {
                if (pref.getEntryValues()[i].toString().equalsIgnoreCase(value)) {
                    // Skip matching values
                    continue;
                }

                entries[outIndex] = pref.getEntries()[i];
                entryValues[outIndex] = pref.getEntryValues()[i];
                outIndex++;
            }

            if (pref.getValue().equalsIgnoreCase(value)) {
                onMatched.run();
            }

            // Update the preference with the new list
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        private void resetRefreshRate(List<Integer> arr, int currentRefreshRate) {

            String preferenceKey = PreferenceConfiguration.FPS_PREF_STRING;

            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            // Create the new arrays
            CharSequence[] entries = new CharSequence[arr.size()];
            CharSequence[] entryValues = new CharSequence[arr.size()];
            int outIndex = 0;
            for (int i = 0; i < arr.size(); i++) {

                int refreshRate = (int) arr.get(i);

                if (currentRefreshRate == refreshRate) {
                    String mark = getResources().getString(R.string.refreshrate_mark);
                    entries[outIndex] = String.valueOf(refreshRate) + " (" + mark + ")";
                } else {
                    entries[outIndex] = String.valueOf(refreshRate);//pref.getEntries()[i];
                }

                entryValues[outIndex] = String.valueOf(refreshRate);//pref.getEntryValues()[i];
                outIndex++;
            }

            // Update the preference with the new list
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        private void resetBitrateToDefault(SharedPreferences prefs, String res, String fps) {
            if (res == null) {
                res = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
            }
            if (fps == null) {
                fps = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS);
            }

            prefs.edit()
                    .putInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                            PreferenceConfiguration.getDefaultBitrate(res, fps))
                    .apply();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            UiHelper.applyStatusBarPadding(view);
            return view;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);
            PreferenceScreen screen = getPreferenceScreen();

            // hide on-screen controls category on non touch screen devices
            if (!getActivity().getPackageManager().
                    hasSystemFeature("android.hardware.touchscreen")) {
                {
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_onscreen_controls");
                    screen.removePreference(category);
                }

                {
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_input_settings");
                    category.removePreference(findPreference("checkbox_touchscreen_trackpad"));
                }
            }

            // Remove PiP mode on devices pre-Oreo, where the feature is not available (some low RAM devices),
            // and on Fire OS where it violates the Amazon App Store guidelines for some reason.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !getActivity().getPackageManager().hasSystemFeature("android.software.picture_in_picture") ||
                    getActivity().getPackageManager().hasSystemFeature("com.amazon.software.fireos")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_ui_settings");
                category.removePreference(findPreference("checkbox_enable_pip"));
            }

            // Remove the vibration options if the device can't vibrate
            if (!((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator()) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_input_settings");
                category.removePreference(findPreference("checkbox_vibrate_fallback"));

                // The entire OSC category may have already been removed by the touchscreen check above
                category = (PreferenceCategory) findPreference("category_onscreen_controls");
                if (category != null) {
                    category.removePreference(findPreference("checkbox_vibrate_osc"));
                }
            }

            int maxSupportedFps = 0;
            ArrayList<Integer> extendRefreshRate = new ArrayList<Integer>();
            Integer[] normalRefreshRate = {30, 60, 90, 120};

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
                int nativeWidth = 0, nativeHeight = 0;
                for (Display.Mode candidate : display.getSupportedModes()) {
                    // Some devices report their dimensions in the portrait orientation
                    // where height > width. Normalize these to the conventional width > height
                    // arrangement before we process them.

                    int width = Math.max(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    int height = Math.min(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());

                    if (width > nativeWidth) {
                        nativeWidth = width;
                    }
                    if (height > nativeHeight) {
                        nativeHeight = height;
                    }

                    if ((width >= 3840 || height >= 2160) && maxSupportedResW < 3840) {
                        maxSupportedResW = 3840;
                    }
                    else if ((width >= 2560 || height >= 1440) && maxSupportedResW < 2560) {
                        maxSupportedResW = 2560;
                    }
                    else if ((width >= 1920 || height >= 1080) && maxSupportedResW < 1920) {
                        maxSupportedResW = 1920;
                    }

                    if (candidate.getRefreshRate() > maxSupportedFps) {
                        maxSupportedFps = (int)candidate.getRefreshRate();
                    }
//                    int refreshRate = (int)candidate.getRefreshRate();
//                    if (!Arrays.asList(normalRefreshRate).contains(refreshRate) && !extendRefreshRate.contains(refreshRate)) {
//                        extendRefreshRate.add(refreshRate);
//                    }
//                    System.out.println("fps " + (int)candidate.getRefreshRate() + " " + width + " x " + height);
                }

                updateNativeResolutionEntry(nativeWidth, nativeHeight);

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

                if (maxSupportedResW != 0) {
                    if (maxSupportedResW < 3840) {
                        // 4K is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_4K, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    if (maxSupportedResW < 2560) {
                        // 1440p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    if (maxSupportedResW < 1920) {
                        // 1080p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_720P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    // Never remove 720p
                }
            }
            else {
                Display display = getActivity().getWindowManager().getDefaultDisplay();
                int width = Math.max(display.getWidth(), display.getHeight());
                int height = Math.min(display.getWidth(), display.getHeight());
                updateNativeResolutionEntry(width, height);
            }

            // 设置
//            extendRefreshRate.add(96); for test
            ArrayList<Integer> supportRefreshRate = new ArrayList<Integer>(extendRefreshRate);
            for (int i=0; i<normalRefreshRate.length; i++) {

                // remove unsupport refresh rate
                if (!PreferenceConfiguration.readPreferences(this.getActivity()).unlockFps && normalRefreshRate[i] > maxSupportedFps) {
                    continue;
                }
                supportRefreshRate.add(normalRefreshRate[i]);
            }

            // add current refresh rate
            int refreshRate = (int)getActivity().getWindowManager().getDefaultDisplay().getRefreshRate();
            if (!supportRefreshRate.contains(refreshRate)) {
                supportRefreshRate.add(refreshRate);
            }

            // sorted
            Collections.sort(supportRefreshRate);

            resetRefreshRate(supportRefreshRate, refreshRate);

//            if (!PreferenceConfiguration.readPreferences(this.getActivity()).unlockFps) {
//                // We give some extra room in case the FPS is rounded down
//                if (maxSupportedFps < 118) {
//                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "120", new Runnable() {
//                        @Override
//                        public void run() {
//                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
//                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "90");
//                            resetBitrateToDefault(prefs, null, null);
//                        }
//                    });
//                }
//                if (maxSupportedFps < 88) {
//                    // 1080p is unsupported
//                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "90", new Runnable() {
//                        @Override
//                        public void run() {
//                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
//                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "60");
//                            resetBitrateToDefault(prefs, null, null);
//                        }
//                    });
//                }
//                // Never remove 30 FPS or 60 FPS
//            }


            // Android L introduces proper 7.1 surround sound support. Remove the 7.1 option
            // for earlier versions of Android to prevent AudioTrack initialization issues.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                LimeLog.info("Excluding 7.1 surround sound option based on OS");
                removeValue(PreferenceConfiguration.AUDIO_CONFIG_PREF_STRING, "71", new Runnable() {
                    @Override
                    public void run() {
                        setValue(PreferenceConfiguration.AUDIO_CONFIG_PREF_STRING, "51");
                    }
                });
            }

            // Android L introduces the drop duplicate behavior of releaseOutputBuffer()
            // that the unlock FPS option relies on to not massively increase latency.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                LimeLog.info("Excluding unlock FPS toggle based on OS");
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_advanced_settings");
                category.removePreference(findPreference("checkbox_unlock_fps"));
            }
            else {
                findPreference(PreferenceConfiguration.UNLOCK_FPS_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        // HACK: We need to let the preference change succeed before reinitializing to ensure
                        // it's reflected in the new layout.
                        final Handler h = new Handler();
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Ensure the activity is still open when this timeout expires
                                StreamSettings settingsActivity = (StreamSettings)SettingsFragment.this.getActivity();
                                if (settingsActivity != null) {
                                    settingsActivity.reloadSettings();
                                }
                            }
                        }, 500);

                        // Allow the original preference change to take place
                        return true;
                    }
                });
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
                if (hdrCaps != null) {
                    // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                    for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            foundHdr10 = true;
                        }
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
            findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    if (nativeResolutionOptionPresent) {
                        // Detect if this value is the native resolution option
                        CharSequence[] values = ((ListPreference)preference).getEntryValues();
                        boolean isNativeRes = true;
                        for (int i = 0; i < values.length; i++) {
                            // If get a match prior to the end, it's not native res
                            if (valueStr.equals(values[i].toString()) && i < values.length - 1) {
                                isNativeRes = false;
                                break;
                            }
                        }

                        // If this is native resolution, show the warning dialog
                        if (isNativeRes) {
                            Dialog.displayDialog(getActivity(),
                                    getResources().getString(R.string.title_native_res_dialog),
                                    getResources().getString(R.string.text_native_res_dialog),
                                    false);
                        }
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, valueStr, null);

                    // Allow the original preference change to take place
                    return true;
                }
            });
            findPreference(PreferenceConfiguration.FPS_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, null, valueStr);

                    // Allow the original preference change to take place
                    return true;
                }
            });
        }
    }

    private static MediaCodecInfo findAvcDecoder() {
        MediaCodecInfo decoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        if (decoder == null) {
            decoder = MediaCodecHelper.findFirstDecoder("video/avc");
        }
        return decoder;
    }
}
