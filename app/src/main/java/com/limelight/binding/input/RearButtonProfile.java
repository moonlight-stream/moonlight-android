package com.limelight.binding.input;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Describes four device-specific Android input events that Iris sends as independent rear controls.
 */
public final class RearButtonProfile {
    /** Current on-disk profile schema version. */
    public static final int SCHEMA_VERSION = 1;

    private final String targetDescriptor;
    private final String targetName;
    private final List<ButtonBinding> bindings;

    /**
     * Creates a rear-button profile.
     *
     * @param targetDescriptor descriptor of the gamepad that receives the mapped inputs
     * @param targetName human-readable name of the target gamepad
     * @param bindings one to four input event bindings in rear-button order
     */
    public RearButtonProfile(String targetDescriptor, String targetName, List<ButtonBinding> bindings) {
        if (targetDescriptor == null || targetDescriptor.isEmpty()) {
            throw new IllegalArgumentException("Target descriptor must not be empty");
        }
        if (bindings == null || bindings.isEmpty() || bindings.size() > 4) {
            throw new IllegalArgumentException("A profile must contain one to four bindings");
        }

        this.targetDescriptor = targetDescriptor;
        this.targetName = targetName == null ? "" : targetName;
        this.bindings = Collections.unmodifiableList(new ArrayList<>(bindings));
    }

    /**
     * Gets the descriptor of the controller that receives rear-button state.
     *
     * @return target controller descriptor
     */
    public String getTargetDescriptor() {
        return targetDescriptor;
    }

    /**
     * Gets the human-readable target controller name.
     *
     * @return target controller name
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * Gets the ordered rear-button bindings.
     *
     * @return immutable binding list
     */
    public List<ButtonBinding> getBindings() {
        return bindings;
    }

    /**
     * Finds the Moonlight rear-button slot for an Android input event.
     *
     * The first two calibrated controls use Moonlight slots three and four,
     * which Prism exposes as the DualSense Edge left and right paddles. Optional
     * third and fourth controls use slots one and two, exposed as Fn1 and Fn2.
     *
     * @param sourceDescriptor descriptor of the device that emitted the event
     * @param keyCode Android key code
     * @param scanCode Linux scan code exposed by Android
     * @return one-based Moonlight rear-button slot, or zero when the event is not mapped
     */
    public int findSlot(String sourceDescriptor, int keyCode, int scanCode) {
        final int[] moonlightSlots = {3, 4, 1, 2};
        for (int i = 0; i < bindings.size(); i++) {
            if (bindings.get(i).matches(sourceDescriptor, keyCode, scanCode)) {
                return moonlightSlots[i];
            }
        }
        return 0;
    }

    /**
     * Serializes this profile.
     *
     * @return JSON representation of the profile
     * @throws JSONException when the JSON object cannot be constructed
     */
    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("targetDescriptor", targetDescriptor);
        object.put("targetName", targetName);

        JSONArray bindingArray = new JSONArray();
        for (ButtonBinding binding : bindings) {
            bindingArray.put(binding.toJson());
        }
        object.put("bindings", bindingArray);
        return object;
    }

    /**
     * Deserializes a rear-button profile.
     *
     * @param object JSON profile object
     * @return parsed profile
     * @throws JSONException when the object is malformed
     */
    public static RearButtonProfile fromJson(JSONObject object) throws JSONException {
        JSONArray bindingArray = object.getJSONArray("bindings");
        List<ButtonBinding> bindings = new ArrayList<>();
        for (int i = 0; i < bindingArray.length(); i++) {
            bindings.add(ButtonBinding.fromJson(bindingArray.getJSONObject(i)));
        }
        return new RearButtonProfile(
                object.getString("targetDescriptor"),
                object.optString("targetName"),
                bindings);
    }

    /**
     * Identifies one physical Android key event.
     */
    public static final class ButtonBinding {
        private final String sourceDescriptor;
        private final String sourceName;
        private final int keyCode;
        private final int scanCode;

        /**
         * Creates an Android input binding.
         *
         * @param sourceDescriptor descriptor of the input event source
         * @param sourceName human-readable input source name
         * @param keyCode Android key code
         * @param scanCode Linux scan code exposed by Android
         */
        public ButtonBinding(String sourceDescriptor, String sourceName, int keyCode, int scanCode) {
            if (sourceDescriptor == null || sourceDescriptor.isEmpty()) {
                throw new IllegalArgumentException("Source descriptor must not be empty");
            }
            this.sourceDescriptor = sourceDescriptor;
            this.sourceName = sourceName == null ? "" : sourceName;
            this.keyCode = keyCode;
            this.scanCode = scanCode;
        }

        /**
         * Tests whether an Android input event matches this binding.
         *
         * @param descriptor input device descriptor
         * @param eventKeyCode Android key code
         * @param eventScanCode Linux scan code exposed by Android
         * @return {@code true} when the event matches
         */
        public boolean matches(String descriptor, int eventKeyCode, int eventScanCode) {
            return sourceDescriptor.equals(descriptor) &&
                    keyCode == eventKeyCode &&
                    scanCode == eventScanCode;
        }

        /**
         * Returns a stable identity string for duplicate detection.
         *
         * @return binding identity
         */
        public String identity() {
            return sourceDescriptor + ":" + keyCode + ":" + scanCode;
        }

        /**
         * Gets the human-readable source device name.
         *
         * @return source name
         */
        public String getSourceName() {
            return sourceName;
        }

        /**
         * Gets the Android key code.
         *
         * @return Android key code
         */
        public int getKeyCode() {
            return keyCode;
        }

        /**
         * Gets the scan code.
         *
         * @return scan code
         */
        public int getScanCode() {
            return scanCode;
        }

        /**
         * Serializes this binding.
         *
         * @return JSON representation of the binding
         * @throws JSONException when the JSON object cannot be constructed
         */
        private JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("sourceDescriptor", sourceDescriptor);
            object.put("sourceName", sourceName);
            object.put("keyCode", keyCode);
            object.put("scanCode", scanCode);
            return object;
        }

        /**
         * Deserializes an input binding.
         *
         * @param object JSON binding object
         * @return parsed binding
         * @throws JSONException when the object is malformed
         */
        private static ButtonBinding fromJson(JSONObject object) throws JSONException {
            return new ButtonBinding(
                    object.getString("sourceDescriptor"),
                    object.optString("sourceName"),
                    object.getInt("keyCode"),
                    object.getInt("scanCode"));
        }
    }
}
