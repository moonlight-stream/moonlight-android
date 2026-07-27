package com.limelight.binding.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;

import com.limelight.LimeLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists and resolves Iris rear-button profiles.
 */
public final class RearButtonProfileStore {
    private static final String PREFERENCES_NAME = "IrisRearButtonProfiles";
    private static final String PROFILES_KEY = "profiles";

    private final SharedPreferences preferences;
    private List<RearButtonProfile> cachedProfiles;

    /**
     * Creates a profile store.
     *
     * @param context application or activity context
     */
    public RearButtonProfileStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        cachedProfiles = readProfiles();
    }

    /**
     * Loads every valid stored profile.
     *
     * Invalid profiles are ignored so a partially damaged preference cannot prevent streaming.
     *
     * @return stored profiles
     */
    public List<RearButtonProfile> loadProfiles() {
        return new ArrayList<>(cachedProfiles);
    }

    /**
     * Parses profiles from persistent storage.
     *
     * @return every valid stored profile
     */
    private List<RearButtonProfile> readProfiles() {
        List<RearButtonProfile> profiles = new ArrayList<>();
        String serialized = preferences.getString(PROFILES_KEY, "");
        if (serialized == null || serialized.isEmpty()) {
            return profiles;
        }

        try {
            JSONObject root = new JSONObject(serialized);
            if (root.getInt("version") != RearButtonProfile.SCHEMA_VERSION) {
                LimeLog.warning("Ignoring unsupported Iris rear-button profile schema");
                return profiles;
            }

            JSONArray profileArray = root.getJSONArray("profiles");
            for (int i = 0; i < profileArray.length(); i++) {
                try {
                    profiles.add(RearButtonProfile.fromJson(profileArray.getJSONObject(i)));
                } catch (JSONException | IllegalArgumentException e) {
                    LimeLog.warning("Ignoring malformed Iris rear-button profile: " + e.getMessage());
                }
            }
        } catch (JSONException e) {
            LimeLog.warning("Unable to read Iris rear-button profiles: " + e.getMessage());
        }
        return profiles;
    }

    /**
     * Adds or replaces the profile for one target controller.
     *
     * @param profile profile to save
     * @return {@code true} when the profile was persisted
     */
    public boolean saveProfile(RearButtonProfile profile) {
        List<RearButtonProfile> profiles = loadProfiles();
        for (int i = profiles.size() - 1; i >= 0; i--) {
            if (profiles.get(i).getTargetDescriptor().equals(profile.getTargetDescriptor())) {
                profiles.remove(i);
            }
        }
        profiles.add(profile);

        try {
            JSONObject root = new JSONObject();
            root.put("version", RearButtonProfile.SCHEMA_VERSION);
            JSONArray profileArray = new JSONArray();
            for (RearButtonProfile storedProfile : profiles) {
                profileArray.put(storedProfile.toJson());
            }
            root.put("profiles", profileArray);
            boolean saved = preferences.edit().putString(PROFILES_KEY, root.toString()).commit();
            if (saved) {
                cachedProfiles = new ArrayList<>(profiles);
            }
            return saved;
        } catch (JSONException e) {
            LimeLog.warning("Unable to save Iris rear-button profile: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes all rear-button profiles.
     */
    public void clearProfiles() {
        preferences.edit().remove(PROFILES_KEY).apply();
        cachedProfiles = new ArrayList<>();
    }

    /**
     * Finds the profile and slot mapped to a key event.
     *
     * @param event Android key event
     * @return resolved mapping, or {@code null} when the event is not mapped
     */
    public ResolvedBinding resolve(KeyEvent event) {
        InputDevice source = event.getDevice();
        if (source == null) {
            return null;
        }

        for (RearButtonProfile profile : loadProfiles()) {
            int slot = profile.findSlot(
                    source.getDescriptor(),
                    event.getKeyCode(),
                    event.getScanCode());
            if (slot != 0) {
                return new ResolvedBinding(profile.getTargetDescriptor(), slot);
            }
        }
        return null;
    }

    /**
     * Tests whether a controller is the target of a stored rear-button profile.
     *
     * @param descriptor controller descriptor
     * @return {@code true} when the controller has a profile
     */
    public boolean hasProfileForTarget(String descriptor) {
        for (RearButtonProfile profile : loadProfiles()) {
            if (profile.getTargetDescriptor().equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Describes a resolved physical event and its target controller.
     */
    public static final class ResolvedBinding {
        /** Descriptor of the controller that receives the event. */
        public final String targetDescriptor;
        /** One-based Moonlight rear-button slot. */
        public final int slot;

        /**
         * Creates a resolved mapping.
         *
         * @param targetDescriptor target controller descriptor
         * @param slot one-based rear-button slot
         */
        private ResolvedBinding(String targetDescriptor, int slot) {
            this.targetDescriptor = targetDescriptor;
            this.slot = slot;
        }
    }
}
