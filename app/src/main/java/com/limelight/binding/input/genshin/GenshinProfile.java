package com.limelight.binding.input.genshin;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Stores and loads Genshin overlay layout profiles in a dedicated SharedPreferences file
 * ("GenshinOverlayProfiles"), completely isolated from Moonlight's original OSC preferences.
 *
 * All coordinates are normalized percentages (0.0..1.0) of the safe-area display bounds so
 * layouts adapt cleanly across 16:9, 19.5:9, 20:9 phones and tablets.
 */
public class GenshinProfile {

    public static final String PREFS_NAME = "GenshinOverlayProfiles";
    public static final String ELEMENT_JOYSTICK_ID = "joystick";

    public static final String PROFILE_DEFAULT = "default";
    public static final String PROFILE_COMPACT = "compact";
    public static final String PROFILE_TABLET  = "tablet";
    public static final String PROFILE_CUSTOM  = "custom";

    public static class ElementLayout {
        public float centerX;   // 0..1 within safeRect width
        public float centerY;   // 0..1 within safeRect height
        public float sizePct;   // diameter relative to safeRect height (e.g. 0.14 = 14% of height)
        public int opacity;     // 10..100, or -1 to inherit global opacity

        public ElementLayout(float centerX, float centerY, float sizePct, int opacity) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.sizePct = sizePct;
            this.opacity = opacity;
        }

        public ElementLayout copy() {
            return new ElementLayout(centerX, centerY, sizePct, opacity);
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("cx", centerX);
            obj.put("cy", centerY);
            obj.put("sz", sizePct);
            obj.put("op", opacity);
            return obj;
        }

        public static ElementLayout fromJson(JSONObject obj, ElementLayout fallback) {
            if (obj == null) {
                return fallback.copy();
            }
            float cx = (float) obj.optDouble("cx", fallback.centerX);
            float cy = (float) obj.optDouble("cy", fallback.centerY);
            float sz = (float) obj.optDouble("sz", fallback.sizePct);
            int op = obj.optInt("op", fallback.opacity);
            return new ElementLayout(
                    clamp(cx, 0.02f, 0.98f),
                    clamp(cy, 0.04f, 0.96f),
                    clamp(sz, 0.05f, 0.45f),
                    op < 0 ? -1 : Math.max(10, Math.min(100, op))
            );
        }
    }

    private final String profileName;
    private final Map<String, ElementLayout> layouts = new HashMap<>();

    public GenshinProfile(String profileName) {
        this.profileName = (profileName == null || profileName.isEmpty()) ? PROFILE_DEFAULT : profileName;
        populateDefaults(this.profileName);
    }

    public String getProfileName() {
        return profileName;
    }

    public ElementLayout getLayout(String elementId) {
        return layouts.get(elementId);
    }

    public void setLayout(String elementId, ElementLayout layout) {
        layouts.put(elementId, layout);
    }

    public void resetToDefaults() {
        layouts.clear();
        populateDefaults(profileName);
    }

    private void populateDefaults(String profile) {
        float scale = 1.0f;
        if (PROFILE_COMPACT.equals(profile)) {
            scale = 0.88f;
        } else if (PROFILE_TABLET.equals(profile)) {
            scale = 0.80f;
        }

        // Floating joystick default resting zone (bottom-left)
        layouts.put(ELEMENT_JOYSTICK_ID, new ElementLayout(0.15f, 0.72f, 0.30f * scale, -1));

        // Primary combat cluster (bottom-right, Genshin Mobile arc around Attack)
        layouts.put(GenshinMapping.Action.ATTACK.id,     new ElementLayout(0.87f, 0.76f, 0.20f * scale, -1));
        layouts.put(GenshinMapping.Action.SPRINT.id,     new ElementLayout(0.76f, 0.86f, 0.135f * scale, -1));
        layouts.put(GenshinMapping.Action.JUMP.id,       new ElementLayout(0.94f, 0.56f, 0.135f * scale, -1));
        layouts.put(GenshinMapping.Action.SKILL_E.id,    new ElementLayout(0.76f, 0.65f, 0.145f * scale, -1));
        layouts.put(GenshinMapping.Action.BURST_Q.id,    new ElementLayout(0.67f, 0.75f, 0.145f * scale, -1));
        layouts.put(GenshinMapping.Action.INTERACT_F.id, new ElementLayout(0.66f, 0.54f, 0.115f * scale, -1));

        // Character party column (mid-right)
        layouts.put(GenshinMapping.Action.CHAR_1.id, new ElementLayout(0.95f, 0.17f, 0.105f * scale, -1));
        layouts.put(GenshinMapping.Action.CHAR_2.id, new ElementLayout(0.95f, 0.28f, 0.105f * scale, -1));
        layouts.put(GenshinMapping.Action.CHAR_3.id, new ElementLayout(0.95f, 0.39f, 0.105f * scale, -1));
        layouts.put(GenshinMapping.Action.CHAR_4.id, new ElementLayout(0.86f, 0.39f, 0.105f * scale, -1));

        // Secondary contextual combat/exploration buttons
        layouts.put(GenshinMapping.Action.AIM_R.id,           new ElementLayout(0.84f, 0.54f, 0.105f * scale, -1));
        layouts.put(GenshinMapping.Action.GADGET_Z.id,        new ElementLayout(0.75f, 0.48f, 0.105f * scale, -1));
        layouts.put(GenshinMapping.Action.SPECIAL_T.id,       new ElementLayout(0.67f, 0.39f, 0.105f * scale, -1));
        layouts.put(GenshinMapping.Action.DROP_X.id,          new ElementLayout(0.58f, 0.86f, 0.105f * scale, -1));
        layouts.put(GenshinMapping.Action.ELEMENTAL_SIGHT.id, new ElementLayout(0.24f, 0.10f, 0.10f * scale, -1));

        // Collapsible top utility bar (left-to-right, compact)
        layouts.put(GenshinMapping.Action.PAIMON_ESC.id,      new ElementLayout(0.06f, 0.10f, 0.095f * scale, -1));
        layouts.put(GenshinMapping.Action.MAP_M.id,           new ElementLayout(0.15f, 0.10f, 0.095f * scale, -1));
        layouts.put(GenshinMapping.Action.QUEST_J.id,         new ElementLayout(0.33f, 0.10f, 0.095f * scale, -1));
        layouts.put(GenshinMapping.Action.QUEST_NAV_V.id,     new ElementLayout(0.42f, 0.10f, 0.095f * scale, -1));
        layouts.put(GenshinMapping.Action.QUICK_WHEEL_TAB.id, new ElementLayout(0.51f, 0.10f, 0.095f * scale, -1));
        layouts.put(GenshinMapping.Action.BAG_B.id,           new ElementLayout(0.73f, 0.10f, 0.095f * scale, -1));
        layouts.put(GenshinMapping.Action.CHARACTER_C.id,     new ElementLayout(0.82f, 0.10f, 0.095f * scale, -1));
    }

    public static GenshinProfile load(Context context, String profileName) {
        GenshinProfile profile = new GenshinProfile(profileName);
        if (context == null) {
            return profile;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString(prefKey(profile.profileName), null);
        if (jsonStr == null) {
            return profile;
        }
        try {
            JSONObject root = new JSONObject(jsonStr);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                ElementLayout fallback = profile.layouts.get(key);
                if (fallback != null) {
                    profile.layouts.put(key, ElementLayout.fromJson(root.optJSONObject(key), fallback));
                }
            }
        } catch (JSONException e) {
            // Corrupt profile data -> fall back to clean defaults
            prefs.edit().remove(prefKey(profile.profileName)).apply();
        }
        return profile;
    }

    public void save(Context context) {
        if (context == null) {
            return;
        }
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, ElementLayout> entry : layouts.entrySet()) {
                root.put(entry.getKey(), entry.getValue().toJson());
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(prefKey(profileName), root.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void clearProfile(Context context, String profileName) {
        if (context == null) {
            return;
        }
        String key = prefKey((profileName == null || profileName.isEmpty()) ? PROFILE_DEFAULT : profileName);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(key).apply();
    }

    public static void clearAllProfiles(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static String prefKey(String profileName) {
        return "profile_" + profileName;
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}
