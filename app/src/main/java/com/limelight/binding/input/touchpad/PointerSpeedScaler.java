package com.limelight.binding.input.touchpad;

/**
 * Scales captured touchpad motion before it reaches the host.
 *
 * Pointer capture hands the application the touchpad's own deltas and so
 * bypasses Android's pointer speed and acceleration curve entirely: the cursor
 * moves faster inside the stream than it does on the tablet itself, and no
 * setting anywhere corrects it. This applies the percentage the user chose.
 *
 * Scaling down loses sub-pixel movement to integer truncation, so each axis
 * carries its remainder into the next event. Without that, a slow drag at 50 %
 * would send nothing at all rather than half the distance.
 *
 * No Android dependency on purpose: this is the part worth testing without a
 * device. Nothing is allocated per event, and the default setting costs a
 * single comparison.
 */
public final class PointerSpeedScaler {

    /** Slowest setting offered: a quarter of the touchpad's own movement. */
    public static final int MIN_PERCENT = 25;

    /** Fastest setting offered: twice the touchpad's own movement. */
    public static final int MAX_PERCENT = 200;

    /** Send the touchpad's movement through unchanged, as upstream does. */
    public static final int DEFAULT_PERCENT = 100;

    private final float factor;
    private final boolean passthrough;

    private float remainderX;
    private float remainderY;

    /**
     * @param percent the user's setting; anything outside
     *                {@link #MIN_PERCENT}..{@link #MAX_PERCENT} is clamped
     *                into range rather than trusted, since a preferences file
     *                can hold any integer
     */
    public PointerSpeedScaler(int percent) {
        int clamped = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));

        this.factor = clamped / 100f;
        this.passthrough = clamped == DEFAULT_PERCENT;
    }

    /** @return the horizontal delta to send, scaled and carrying its remainder */
    public short scaleX(int delta) {
        if (passthrough) {
            return clampToShort(delta);
        }

        float scaled = bound(delta * factor + remainderX);
        int whole = (int) scaled;
        remainderX = scaled - whole;

        return (short) whole;
    }

    /** @return the vertical delta to send, scaled and carrying its remainder */
    public short scaleY(int delta) {
        if (passthrough) {
            return clampToShort(delta);
        }

        float scaled = bound(delta * factor + remainderY);
        int whole = (int) scaled;
        remainderY = scaled - whole;

        return (short) whole;
    }

    /**
     * Keeps a scaled delta inside what the protocol can carry. Bounding here
     * rather than on the way out also keeps the carried remainder below one
     * unit, which is the whole point of carrying it.
     */
    private static float bound(float scaled) {
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
    }

    private static short clampToShort(int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}
