package com.limelight.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Vibrator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.bytehamster.lib.preferencesearch.SearchConfiguration;
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult;
import com.bytehamster.lib.preferencesearch.SearchPreference;
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Range;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.limelight.DebugInfoActivity;
import com.limelight.BuildConfig;
import com.limelight.GameMenu;
import com.limelight.LimeLog;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.FileUriUtils;
import com.limelight.utils.UiHelper;
import org.json.JSONObject;
import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class StreamSettings extends AppCompatActivity implements SearchPreferenceResultListener {
    private PreferenceConfiguration previousPrefs;
    private int previousDisplayPixelCount;

    private SettingsFragment prefsFragment;

    // HACK for Android 9
    static DisplayCutout displayCutoutP;

    void reloadSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();
            previousDisplayPixelCount = mode.getPhysicalWidth() * mode.getPhysicalHeight();
        }
        prefsFragment = new SettingsFragment(PreferenceConfiguration.readPreferences(this));
        getSupportFragmentManager().beginTransaction().replace(
                R.id.stream_settings, prefsFragment
        ).commitAllowingStateLoss();
    }

    @Override
    public void onSearchResultClicked(SearchPreferenceResult result) {
        result.closeSearchPage(this);
        result.highlight(prefsFragment);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        previousPrefs = PreferenceConfiguration.readPreferences(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_stream_settings);

        UiHelper.notifyNewRootView(this);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // We have to use this hack on Android 9 because we don't have Display.getCutout()
        // which was added in Android 10.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Insets can be null when the activity is recreated on screen rotation
            // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                displayCutoutP = insets.getDisplayCutout();
            }
        }

        reloadSettings();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();

            // If the display's physical pixel count has changed, we consider that it's a new display
            // and we should reload our settings (which include display-dependent values).
            //
            // NB: We aren't using displayId here because that stays the same (DEFAULT_DISPLAY) when
            // switching between screens on a foldable device.
            if (mode.getPhysicalWidth() * mode.getPhysicalHeight() != previousDisplayPixelCount) {
                reloadSettings();
            }
        }
    }

    @Override
    // NOTE: This will NOT be called on Android 13+ with android:enableOnBackInvokedCallback="true"
    public void onBackPressed() {
        finish();

        // Language changes are handled via configuration changes in Android 13+,
        // so manual activity relaunching is no longer required.
        PreferenceConfiguration newPrefs = PreferenceConfiguration.readPreferences(this);
        if (!newPrefs.language.equals(previousPrefs.language)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Restart the PC view to apply UI changes
                Intent intent = new Intent(this, PcView.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent, null);
            } else {
                if (newPrefs.language == PreferenceConfiguration.DEFAULT_LANGUAGE) {
                    Toast.makeText(this, "Language has been reset to default, please restart the app!", Toast.LENGTH_LONG).show();
                    System.exit(0);
                }
            }
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private int nativeResolutionStartIndex = Integer.MAX_VALUE;
        private boolean nativeFramerateShown = false;

        private PreferenceConfiguration prevPrefConfig;

        public SettingsFragment(PreferenceConfiguration prefCfg) {
            prevPrefConfig = prefCfg;
        }

        private void setValue(String preferenceKey, String value) {
            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            pref.setValue(value);
        }

        private void appendPreferenceEntry(ListPreference pref, String newEntryName, String newEntryValue) {
            CharSequence[] newEntries = Arrays.copyOf(pref.getEntries(), pref.getEntries().length + 1);
            CharSequence[] newValues = Arrays.copyOf(pref.getEntryValues(), pref.getEntryValues().length + 1);

            // Add the new option
            newEntries[newEntries.length - 1] = newEntryName;
            newValues[newValues.length - 1] = newEntryValue;

            pref.setEntries(newEntries);
            pref.setEntryValues(newValues);
        }

        private void addNativeResolutionEntry(int nativeWidth, int nativeHeight, boolean insetsRemoved, boolean portrait, boolean is_custom) {
            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING);

            String newName;

            if (insetsRemoved) {
                newName = getResources().getString(R.string.resolution_prefix_native_fullscreen);
            }
            else {
                newName = is_custom ? getResources().getString(R.string.resolution_prefix_custom) : getResources().getString(R.string.resolution_prefix_native);
            }

            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                if (portrait) {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_portrait);
                }
                else {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_landscape);
                }
            }

            newName += " ("+nativeWidth+"x"+nativeHeight+")";

            String newValue = nativeWidth+"x"+nativeHeight;

            // Check if the native resolution is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (newValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    return;
                }
            }

            if (pref.getEntryValues().length < nativeResolutionStartIndex) {
                nativeResolutionStartIndex = pref.getEntryValues().length;
            }
            appendPreferenceEntry(pref, newName, newValue);
        }

        private void addNativeResolutionEntries(int nativeWidth, int nativeHeight, boolean insetsRemoved, boolean is_custom) {
            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                addNativeResolutionEntry(nativeHeight, nativeWidth, insetsRemoved, true, is_custom);
            }
            addNativeResolutionEntry(nativeWidth, nativeHeight, insetsRemoved, false, is_custom);
        }

        private void addNativeFrameRateEntry(float framerate, boolean is_custom) {
            if (!is_custom) {
                framerate = Math.round(framerate);
                if (framerate == 0) {
                    return;
                }
            }

            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.FPS_PREF_STRING);
            String fpsValue = is_custom ? Float.toString(framerate) : Integer.toString(Math.round(framerate));
            String fpsName = (is_custom ? getResources().getString(R.string.resolution_prefix_custom) : getResources().getString(R.string.resolution_prefix_native)) +
                    " (" + fpsValue + " " + getResources().getString(R.string.fps_suffix_fps) + ")";

            // Check if the native frame rate is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (fpsValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    nativeFramerateShown = false;
                    return;
                }
            }

            appendPreferenceEntry(pref, fpsName, fpsValue);
            nativeFramerateShown = true;
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
        public void onCreatePreferences(Bundle bundle, String s) {}

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            initializePreferences();

            SearchPreference searchPreference = findPreference("searchPreference");
            assert searchPreference != null;
            SearchConfiguration config = searchPreference.getSearchConfiguration();
            config.setActivity((AppCompatActivity) requireActivity());
            config.index(R.xml.preferences);
        }

        public void initializePreferences() {
            addPreferencesFromResource(R.xml.preferences);
            PreferenceScreen screen = getPreferenceScreen();

            AppCompatActivity activity = (AppCompatActivity) requireActivity();
            PackageManager pm = activity.getPackageManager();

            // hide on-screen controls category on non touch screen devices
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                PreferenceCategory category = findPreference("category_onscreen_controls");
                if (category != null) {
                    screen.removePreference(category);
                }
                category = findPreference("category_special_key_layout");
                if (category != null) {
                    screen.removePreference(category);
                }
            }

            // Hide remote desktop mouse mode on pre-Oreo (which doesn't have pointer capture)
            // and NVIDIA SHIELD devices (which support raw mouse input in pointer capture mode)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    getActivity().getPackageManager().hasSystemFeature("com.nvidia.feature.shield")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_input_settings");
                category.removePreference(findPreference("checkbox_absolute_mouse_mode"));
            }

            // Hide gamepad motion sensor option when running on OSes before Android 12.
            // Support for motion, LED, battery, and other extensions were introduced in S.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_gamepad_motion_sensors"));
            }

            // Hide gamepad motion sensor fallback option if the device has no gyro or accelerometer
            if (!pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) &&
                    !activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_force_device_motion"));
                category.removePreference(findPreference("checkbox_gamepad_motion_fallback"));
            }

            // Hide USB driver options on devices without USB host support
            if (!pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_usb_bind_all"));
                category.removePreference(findPreference("checkbox_usb_driver"));
            }

            // Remove PiP mode on devices pre-Oreo, where the feature is not available (some low RAM devices),
            // and on Fire OS where it violates the Amazon App Store guidelines for some reason.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !pm.hasSystemFeature("android.software.picture_in_picture") ||
                    pm.hasSystemFeature("com.amazon.software.fireos")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_ui_settings");
                category.removePreference(findPreference("checkbox_enable_pip"));
            }

            // Fire TV apps are not allowed to use WebViews or browsers, so hide the Help category
            /*if (getActivity().getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_help");
                screen.removePreference(category);
            }*/
            PreferenceCategory category_gamepad_settings =
                    (PreferenceCategory) findPreference("category_gamepad_settings");
            // Remove the vibration options if the device can't vibrate
            if (!((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator()) {
                category_gamepad_settings.removePreference(findPreference("checkbox_vibrate_fallback"));
                category_gamepad_settings.removePreference(findPreference("seekbar_vibrate_fallback_strength"));
                // The entire OSC category may have already been removed by the touchscreen check above
                PreferenceCategory category = findPreference("category_onscreen_controls");
                if (category != null) {
                    category.removePreference(findPreference("checkbox_vibrate_osc"));
                }
                category = findPreference("category_special_key_layout");
                if (category != null) {
                    category.removePreference(findPreference("checkbox_vibrate_keyboard"));
                }
                category = findPreference("category_gamepad_settings");
                if (category != null) {
                    category.removePreference(findPreference("checkbox_enable_device_rumble"));
                }
            }
            else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasAmplitudeControl()) {
                // Remove the vibration strength selector of the device doesn't have amplitude control
                category_gamepad_settings.removePreference(findPreference("seekbar_vibrate_fallback_strength"));
            }

            // Check custom resolution
            String customResStr = prevPrefConfig.customResolution;
            if(customResStr != null && !customResStr.isEmpty()){
                String[] resolutionSegments = customResStr.split("x");
                if(resolutionSegments.length == 2){
                    try {
                        addNativeResolutionEntries(Integer.parseInt(resolutionSegments[0]), Integer.parseInt(resolutionSegments[1]), false, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            
            // Check custom refresh rate
            String customRefreshRateStr = prevPrefConfig.customRefreshRate;
            if (customRefreshRateStr != null && !customRefreshRateStr.isEmpty()) {
                try {
                    float customRefreshRateValue = Float.parseFloat(customRefreshRateStr);
                    if (customRefreshRateValue > 0) {
                        addNativeFrameRateEntry(customRefreshRateValue, true);
                    }
                } catch (NumberFormatException e) {
                    PreferenceManager.getDefaultSharedPreferences(this.getActivity()).edit().remove(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING).apply();
                }
            }

            Display display = activity.getWindowManager().getDefaultDisplay();
            float maxSupportedFps = display.getRefreshRate();

            // Hide non-supported resolution/FPS combinations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int maxSupportedResW = 0;

                // Add a native resolution with any insets included for users that don't want content
                // behind the notch of their display
                boolean hasInsets = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use the much nicer Display.getCutout() API on Android 10+
                        cutout = display.getCutout();
                    }
                    else {
                        // Android 9 only
                        cutout = displayCutoutP;
                    }

                    if (cutout != null) {
                        int widthInsets = cutout.getSafeInsetLeft() + cutout.getSafeInsetRight();
                        int heightInsets = cutout.getSafeInsetBottom() + cutout.getSafeInsetTop();

                        if (widthInsets != 0 || heightInsets != 0) {
                            DisplayMetrics metrics = new DisplayMetrics();
                            display.getRealMetrics(metrics);

                            int width = Math.max(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);
                            int height = Math.min(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);

                            addNativeResolutionEntries(width, height, false, false);
                            hasInsets = true;
                        }
                    }
                }

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

                    // Some TVs report strange values here, so let's avoid native resolutions on a TV
                    // unless they report greater than 4K resolutions.
                    if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                            (width > 3840 || height > 2160)) {
                        addNativeResolutionEntries(width, height, hasInsets, false);
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
                        maxSupportedFps = candidate.getRefreshRate();
                    }
                }

                // This must be called to do runtime initialization before calling functions that evaluate
                // decoder lists.
                MediaCodecHelper.initialize(getContext(), GlPreferences.readPreferences(requireContext()).glRenderer);

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
                // We can get the true metrics via the getRealMetrics() function (unlike the lies
                // that getWidth() and getHeight() tell to us).
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                int width = Math.max(metrics.widthPixels, metrics.heightPixels);
                int height = Math.min(metrics.widthPixels, metrics.heightPixels);
                addNativeResolutionEntries(width, height, false, false);
            }

            if (!PreferenceConfiguration.readPreferences(this.getActivity()).unlockFps) {
                // We give some extra room in case the FPS is rounded down
                if (maxSupportedFps < 118) {
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "120", new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "90");
                            resetBitrateToDefault(prefs, null, null);
                        }
                    });
                }
                if (maxSupportedFps < 88) {
                    // 1080p is unsupported
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "90", new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "60");
                            resetBitrateToDefault(prefs, null, null);
                        }
                    });
                }
                // Never remove 30 FPS or 60 FPS
            }
            addNativeFrameRateEntry(maxSupportedFps, false);

            // Android L introduces the drop duplicate behavior of releaseOutputBuffer()
            // that the unlock FPS option relies on to not massively increase latency.
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
                            StreamSettings settingsActivity = (StreamSettings) SettingsFragment.this.getActivity();
                            if (settingsActivity != null) {
                                settingsActivity.reloadSettings();
                            }
                        }
                    }, 500);

                    // Allow the original preference change to take place
                    return true;
                }
            });

            // Remove HDR preference for devices below Nougat
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                LimeLog.info("Excluding HDR toggle based on OS");
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_video_settings");
                category.removePreference(findPreference("checkbox_enable_hdr"));
            }
            else {
                Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();

                // We must now ensure our display is compatible with HDR10
                boolean foundHdr10 = false;
                if (hdrCaps != null) {
                    // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                    for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            foundHdr10 = true;
                            break;
                        }
                    }
                }

                if (!foundHdr10) {
                    LimeLog.info("Excluding HDR toggle based on display capabilities");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_video_settings");
                    category.removePreference(findPreference("checkbox_enable_hdr"));
                }
                else if (PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()) {
                    LimeLog.info("Disabling HDR toggle on old broken SHIELD TV firmware");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_video_settings");
                    CheckBoxPreference hdrPref = (CheckBoxPreference) category.findPreference("checkbox_enable_hdr");
                    hdrPref.setEnabled(false);
                    hdrPref.setChecked(false);
                    hdrPref.setSummary("Update the firmware on your NVIDIA SHIELD Android TV to enable HDR");
                }
            }

            // Add a listener to the FPS and resolution preference
            // so the bitrate can be auto-adjusted
            findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    // Detect if this value is the native resolution option
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    boolean isNativeRes = true;
                    for (int i = 0; i < values.length; i++) {
                        // Look for a match prior to the start of the native resolution entries
                        if (valueStr.equals(values[i].toString()) && i < nativeResolutionStartIndex) {
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

                    // If this is native frame rate, show the warning dialog
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    if (nativeFramerateShown && values[values.length - 1].toString().equals(newValue.toString())) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_fps_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, null, valueStr);

                    // Allow the original preference change to take place
                    return true;
                }
            });

            Preference _pref;
            _pref = findPreference("import_keyboard_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        startActivityForResult(intent, READ_REQUEST_CODE);
                        return false;
                    }
                });
            }

            _pref = findPreference("import_special_button_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        startActivityForResult(intent, READ_REQUEST_SPECIAL_CODE);
                        return false;
                    }
                });
            }

            _pref = findPreference("export_keyboard_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        File file = new File(requireActivity().getExternalCacheDir(),"export_settings");
                        if(!file.exists()){
                            file.mkdir();
                        }
                        File file1= getJsonContent(requireActivity(),file);
                        if(file1==null){
                            Toast.makeText(requireActivity(),getString(R.string.pref_error_occurred),Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        Uri uri;
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        String authority= BuildConfig.APPLICATION_ID+".fileprovider";
                        uri = FileProvider.getUriForFile(requireActivity(),authority,file1);
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        intent.setType("application/json");
                        startActivity(Intent.createChooser(intent,getString(R.string.pref_save_keyboard_profile)));
                        return false;
                    }
                });
            }

            _pref = findPreference("pref_debug_info");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(@NonNull Preference preference) {
                        Intent intent=new Intent(requireActivity(), DebugInfoActivity.class);
                        requireActivity().startActivity(intent);
                        return false;
                    }
                });
            }

            EditTextPreference bitrateEditPre = findPreference(PreferenceConfiguration.CUSTOM_BITRATE_PREF_STRING);
            if (bitrateEditPre != null) {
                bitrateEditPre.setOnBindEditTextListener((EditText editText) -> {
                    editText.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)/*这里限制输入的长度为5个字母*/});
                });

                bitrateEditPre.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    float bitrateValue = Float.parseFloat(value) * 1000;
                    LimeLog.info("axi-bitrateValue:" + bitrateValue);
                    int bitrate = (int) bitrateValue;
                    LimeLog.info("axi-bitrate:" + bitrate);
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    prefs.edit().putInt(PreferenceConfiguration.BITRATE_PREF_STRING, bitrate).apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_set_success), Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            EditTextPreference resolutionEditPref = findPreference(PreferenceConfiguration.CUSTOM_RESOLUTION_PREF_STRING);
            if (resolutionEditPref != null) {
                resolutionEditPref.setOnBindEditTextListener((EditText editText) -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT);
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(11)});
                });

                resolutionEditPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    // Verify format: [width]x[height]
                    String[] resolutionSegments = value.split("x");
                    if (resolutionSegments.length != 2) {
                        Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    try {
                        int width = Integer.parseInt(resolutionSegments[0]);
                        int height = Integer.parseInt(resolutionSegments[1]);
                        
                        if (width <= 0 || height <= 0) {
                            Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                            return false;
                        }

                        // Save the value and reload settings
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                        prefs.edit().putString(PreferenceConfiguration.CUSTOM_RESOLUTION_PREF_STRING, value).apply();
                        
                        // HACK: We need to let the preference change succeed before reinitializing to ensure
                        // it's reflected in the new layout.
                        final Handler h = new Handler();
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Ensure the activity is still open when this timeout expires
                                StreamSettings settingsActivity = (StreamSettings) SettingsFragment.this.getActivity();
                                if (settingsActivity != null) {
                                    settingsActivity.reloadSettings();
                                }
                            }
                        }, 500);
                        
                        return true;
                    } catch (NumberFormatException e) {
                        Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                });
            }

            EditTextPreference customRefreshRatePref = findPreference(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING);
            if (customRefreshRatePref != null) {
                customRefreshRatePref.setOnBindEditTextListener((EditText editText) -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
                });

                customRefreshRatePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    
                    try {
                        float refreshRate = Float.parseFloat(value);
                        if (refreshRate <= 0) {
                            Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        
                        // Format to max 3 decimal places
                        String formattedValue = String.format("%.3f", refreshRate);
                        // Remove trailing zeros
                        formattedValue = formattedValue.replaceAll("0+$", "").replaceAll("\\.$", "");

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                        prefs.edit().putString(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING, formattedValue).apply();
                        
                        // HACK: We need to let the preference change succeed before reinitializing to ensure
                        // it's reflected in the new layout.
                        final Handler h = new Handler();
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Ensure the activity is still open when this timeout expires
                                StreamSettings settingsActivity = (StreamSettings) SettingsFragment.this.getActivity();
                                if (settingsActivity != null) {
                                    settingsActivity.reloadSettings();
                                }
                            }
                        }, 500);
                        
                        return true;
                    } catch (NumberFormatException e) {
                        Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                });
            }
        }

        int READ_REQUEST_CODE = 1001;
        int READ_REQUEST_SPECIAL_CODE = 1002;

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK && data.getData() != null) {
                try {
                    Uri uri = data.getData();
                    String json = FileUriUtils.openUriForRead(getActivity(), uri);
                    if (TextUtils.isEmpty(json)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_empty_file), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String name = PreferenceManager.getDefaultSharedPreferences(requireActivity()).getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
                    SharedPreferences.Editor prefEditor = requireActivity().getSharedPreferences(name, Activity.MODE_PRIVATE).edit();
                    JSONObject object = new JSONObject(json);
                    Iterator it = object.keys();
                    prefEditor.clear();
                    while (it.hasNext()) {
                        String key = (String) it.next();// 获得key
                        String value = object.getString(key);// 获得value
                        prefEditor.putString(key, value);
                    }
                    prefEditor.apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), getString(R.string.pref_error_occurred) + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                return;
            }

            if (requestCode == READ_REQUEST_SPECIAL_CODE && resultCode == Activity.RESULT_OK && data.getData() != null) {
                try {
                    Uri uri = data.getData();
                    String json = FileUriUtils.openUriForRead(getActivity(), uri);
                    if (TextUtils.isEmpty(json)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_empty_file), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SharedPreferences.Editor prefEditor = getActivity().getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE).edit();
                    prefEditor.putString(GameMenu.KEY_NAME, json);
                    prefEditor.apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), getString(R.string.pref_error_occurred) + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            if (preference instanceof ConfirmDeleteOscPreference) {
                DialogFragment dialogFragment = ConfirmDeleteOscPreference.DialogFragmentCompat.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getFragmentManager(), null);
            } else if (preference instanceof ConfirmDeleteKeyboardPreference) {
                DialogFragment dialogFragment = ConfirmDeleteKeyboardPreference.DialogFragmentCompat.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getFragmentManager(), null);
            } else super.onDisplayPreferenceDialog(preference);
        }

        private File getJsonContent(Context context,File file){
            String name = PreferenceManager.getDefaultSharedPreferences(context).getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
            SharedPreferences pref = context.getSharedPreferences(name, Activity.MODE_PRIVATE);
            Map<String,?> map = pref.getAll();
            File file1= new File(file,name+".json");
            String jsonStr=new Gson().toJson(map);
            if(!FileUriUtils.writerFileString(file1,jsonStr)){
                return null;
            }
            return file1;
        }

        //获取所有设置项配置文件
        private File getAllJsonData(Context context,File file){
            SharedPreferences pref=PreferenceManager.getDefaultSharedPreferences(context);
            Map<String,?> map = pref.getAll();
            //获取适配电脑的数据库信息
//            List<ComputerDetails> map= new ComputerDatabaseManager(context).getAllComputers();
            File file1= new File(file,"allJSON.json");
            String jsonStr=new Gson().toJson(map);
            if(!FileUriUtils.writerFileString(file1,jsonStr)){
                return null;
            }
            return file1;
        }
    }
}

