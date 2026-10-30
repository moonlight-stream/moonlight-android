package com.limelight.binding.input.touchpad;

import com.limelight.nvstream.input.MouseButtonPacket;

/**
 * Recognises trackpad taps and two-finger scrolling from bare finger positions.
 *
 * While the stream holds pointer capture, Android stops running its own
 * touchpad gesture layer and hands the application the fingers themselves.
 * A physical click still arrives as a button because it is one in hardware,
 * but tap-to-click and two-finger scrolling are pure software on Android's
 * side and simply stop happening. This class puts them back.
 *
 * Distances arrive in the touchpad's own units, whose scale differs per
 * device, so every threshold here is expressed as a fraction of the pad's
 * height rather than as a count of units.
 *
 * No Android dependency on purpose: this is the part worth testing without
 * a device. {@link CapturedTouchpadGestures} is the adapter that feeds it.
 */
public final class TouchpadGestureTracker {

    /** Where a recognised gesture goes. */
    public interface MouseOutput {
        /** A full press and release of {@code button}. */
        void click(byte button);

        /** Vertical wheel movement, in high-resolution units; 120 per notch. */
        void scroll(short highResolutionUnits);
    }

    /** Longer than this and the touch is a move the user cut short, not a tap. */
    private static final long TAP_MAX_DURATION_MS = 250;

    /** Movement allowed within a tap, as a fraction of the pad's height. */
    private static final float TAP_SLOP_FRACTION = 0.04f;

    /** Dragging two fingers the full height of the pad turns the wheel this much. */
    private static final float SCROLL_NOTCHES_PER_PAD_HEIGHT = 14f;

    /** High-resolution units in one wheel notch, as the protocol counts them. */
    private static final float WHEEL_NOTCH_UNITS = 120f;

    /** Used when the device does not report its own vertical range. */
    private static final float FALLBACK_PAD_HEIGHT = 2000f;

    private final MouseOutput output;
    private final float tapSlop;
    private final float scrollUnitsPerPadUnit;

    private boolean gestureActive;
    private boolean disqualified;
    private boolean movedPastSlop;
    private int fingersDown;
    private int maxFingersInGesture;
    private long startTime;
    private float startX;
    private float startY;
    private float lastX;
    private float lastY;
    private float scrollRemainder;

    /**
     * @param padHeight the touchpad's vertical range in its own units;
     *                  anything non-positive falls back to a default
     */
    public TouchpadGestureTracker(MouseOutput output, float padHeight) {
        this.output = output;

        float height = padHeight > 0 ? padHeight : FALLBACK_PAD_HEIGHT;
        this.tapSlop = height * TAP_SLOP_FRACTION;
        this.scrollUnitsPerPadUnit = SCROLL_NOTCHES_PER_PAD_HEIGHT * WHEEL_NOTCH_UNITS / height;
    }

    /** First finger down: the gesture starts here and nowhere else. */
    public void beginGesture(long eventTime, float x, float y) {
        gestureActive = true;
        disqualified = false;
        movedPastSlop = false;
        fingersDown = 1;
        maxFingersInGesture = 1;
        startTime = eventTime;
        startX = x;
        startY = y;
        lastX = x;
        lastY = y;
        scrollRemainder = 0;
    }

    /** A further finger landed; {@code fingerCount} counts them all. */
    public void fingerAdded(int fingerCount) {
        if (!gestureActive) {
            return;
        }

        fingersDown = fingerCount;
        if (fingerCount > maxFingersInGesture) {
            maxFingersInGesture = fingerCount;
        }
    }

    /** The fingers moved; {@code x} and {@code y} track the first one down. */
    public void fingersMoved(int fingerCount, float x, float y) {
        if (!gestureActive) {
            return;
        }

        fingersDown = fingerCount;

        boolean wasPastSlop = movedPastSlop;
        if (!movedPastSlop &&
                (Math.abs(x - startX) > tapSlop || Math.abs(y - startY) > tapSlop)) {
            movedPastSlop = true;
        }

        // Two fingers past the tap slop is a scroll. The slop itself is spent
        // deciding that, so the move that crosses it is not scrolled through:
        // otherwise a slightly sloppy two-finger tap nudges the wheel.
        if (wasPastSlop && fingerCount == 2 && !disqualified) {
            float units = (y - lastY) * scrollUnitsPerPadUnit + scrollRemainder;
            int whole = (int) units;
            scrollRemainder = units - whole;

            if (whole != 0) {
                output.scroll(clampToShort(whole));
            }
        }

        lastX = x;
        lastY = y;
    }

    /**
     * The finger whose position we follow has left the pad and another one
     * takes over at ({@code x}, {@code y}).
     *
     * The fingers of one gesture sit centimetres apart, so adopting the new
     * one's position as-is would read as a sudden jump and cancel the tap the
     * user is in the middle of making. The gesture's travel so far is what
     * matters, and it is carried over unchanged.
     */
    public void retargetFinger(float x, float y) {
        if (!gestureActive) {
            return;
        }

        startX += x - lastX;
        startY += y - lastY;
        lastX = x;
        lastY = y;
    }

    /** One finger lifted while {@code remainingFingers} stay down. */
    public void fingerRemoved(int remainingFingers) {
        if (!gestureActive) {
            return;
        }

        fingersDown = remainingFingers;
    }

    /** The last finger lifted: this is where a tap becomes a click. */
    public void endGesture(long eventTime) {
        if (!gestureActive) {
            return;
        }

        gestureActive = false;
        fingersDown = 0;

        if (disqualified || movedPastSlop || eventTime - startTime > TAP_MAX_DURATION_MS) {
            return;
        }

        // Three fingers and up are reserved: those are the system's own
        // gestures, and claiming them would make the stream fight the tablet.
        if (maxFingersInGesture == 1) {
            output.click(MouseButtonPacket.BUTTON_LEFT);
        }
        else if (maxFingersInGesture == 2) {
            output.click(MouseButtonPacket.BUTTON_RIGHT);
        }
    }

    /** The gesture was taken away from us, by the system or by a lost focus. */
    public void cancelGesture() {
        gestureActive = false;
        fingersDown = 0;
    }

    /**
     * The physical button is involved, so hardware is already reporting this
     * press: no tap and no scroll, or the host would see the click twice.
     */
    public void disqualify() {
        disqualified = true;
    }

    /**
     * True while more than one finger is down, which is the whole of the
     * gesture's vocabulary: the caller must hold back pointer motion for as
     * long as it lasts, or the cursor wanders off while the user scrolls.
     */
    public boolean isMultiFingerGestureActive() {
        return gestureActive && fingersDown >= 2;
    }

    private static short clampToShort(int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}
