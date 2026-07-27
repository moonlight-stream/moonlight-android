package com.limelight.binding.input;

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
}
