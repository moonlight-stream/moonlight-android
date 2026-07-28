package com.limelight.binding.input;

import com.limelight.nvstream.jni.MoonBridge;

/**
 * Resolves controller capabilities that may be supplied by either a gamepad or the handheld.
 */
public final class ControllerCapabilities {
    private ControllerCapabilities() {
    }

    /**
     * Determines whether Iris should advertise standard rumble support.
     *
     * Device-vibration fallback is limited to player one because Iris routes the
     * handheld vibrator to controller zero.
     *
     * @param nativeRumbleAvailable whether Android exposes a vibrator on the gamepad
     * @param fallbackEnabled whether handheld vibration fallback is enabled
     * @param fallbackVibratorAvailable whether the Android device has a vibrator
     * @param controllerNumber Moonlight controller number
     * @return whether the controller arrival packet should advertise rumble
     */
    public static boolean shouldAdvertiseRumble(boolean nativeRumbleAvailable,
                                                boolean fallbackEnabled,
                                                boolean fallbackVibratorAvailable,
                                                int controllerNumber) {
        return nativeRumbleAvailable ||
                (fallbackEnabled && fallbackVibratorAvailable && controllerNumber == 0);
    }

    /**
     * Determines whether a connected Android input device should be announced
     * immediately when streaming begins.
     *
     * @param hasRearButtonProfile whether the device is a saved profile target
     * @param gameControllerDevice whether Android classifies it as a controller
     * @return whether Iris should announce the device before its first input event
     */
    public static boolean shouldAnnounceOnConnection(boolean hasRearButtonProfile,
                                                     boolean gameControllerDevice) {
        return hasRearButtonProfile && gameControllerDevice;
    }

    /**
     * Determines whether Iris should attach the handheld motion sensors.
     *
     * A calibrated rear-button controller explicitly requests DualSense Edge
     * emulation, so its motion feature is supplied automatically when general
     * gamepad motion remains enabled. Other controllers continue to honor the
     * explicit handheld fallback preference.
     *
     * @param fallbackEnabled whether the handheld motion fallback preference is enabled
     * @param dualSenseEdgeProfile whether the controller has a calibrated Edge profile
     * @param gamepadMotionEnabled whether gamepad motion is globally enabled
     * @param firstController whether this is player one's controller
     * @param controllerSensorsAvailable whether the controller exposes its own sensors
     * @return whether to attach the handheld sensor manager
     */
    public static boolean shouldUseDeviceMotion(boolean fallbackEnabled,
                                                boolean dualSenseEdgeProfile,
                                                boolean gamepadMotionEnabled,
                                                boolean firstController,
                                                boolean controllerSensorsAvailable) {
        return firstController && !controllerSensorsAvailable &&
                (fallbackEnabled || (dualSenseEdgeProfile && gamepadMotionEnabled));
    }

    /**
     * Resolves the controller family sent to Prism.
     *
     * Calibrated rear controls require a DualSense Edge and therefore report
     * the PlayStation family even when Android labels the built-in pad as Xbox.
     * Motion-emulated non-PlayStation controllers retain Moonlight's automatic
     * host-selection behavior by reporting an unknown family.
     *
     * @param detectedType controller family detected from Android metadata
     * @param dualSenseEdgeProfile whether the controller has a calibrated Edge profile
     * @param motionAvailable whether controller or handheld motion sensors are attached
     * @return Moonlight controller family reported to Prism
     */
    public static byte resolveReportedType(byte detectedType,
                                           boolean dualSenseEdgeProfile,
                                           boolean motionAvailable) {
        if (dualSenseEdgeProfile) {
            return MoonBridge.LI_CTYPE_PS;
        }
        if (detectedType != MoonBridge.LI_CTYPE_PS && motionAvailable) {
            return MoonBridge.LI_CTYPE_UNKNOWN;
        }
        return detectedType;
    }
}
