package com.limelight.binding.input.touch;

import android.os.SystemClock;
import android.view.View;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;

import java.util.Timer;
import java.util.TimerTask;

public class RelativeTouchContext implements TouchContext {
    private int lastTouchX = 0;
    private int lastTouchY = 0;
    private int originalTouchX = 0;
    private int originalTouchY = 0;
    private long originalTouchTime = 0;
    private boolean cancelled;
    private boolean confirmedMove;
    private boolean confirmedDrag;
    private boolean confirmedScroll;
    private Timer dragTimer;
    private double distanceMoved;
    private double xFactor, yFactor;
    private int pointerCount;
    private int maxPointerCountInGesture;

    private final NvConnection conn;
    private final int actionIndex;
    private final int referenceWidth;
    private final int referenceHeight;
    private final View targetView;

    private static final int TAP_MOVEMENT_THRESHOLD = 20;
    private static final int TAP_DISTANCE_THRESHOLD = 25;
    private static final int TAP_TIME_THRESHOLD = 250;
    private static final int DRAG_TIME_THRESHOLD = 650;

    private static final int SCROLL_SPEED_FACTOR = 5;

    public RelativeTouchContext(NvConnection conn, int actionIndex,
                                int referenceWidth, int referenceHeight, View view)
    {
        this.conn = conn;
        this.actionIndex = actionIndex;
        this.referenceWidth = referenceWidth;
        this.referenceHeight = referenceHeight;
        this.targetView = view;
    }

    @Override
    public int getActionIndex()
    {
        return actionIndex;
    }

    private boolean isWithinTapBounds(int touchX, int touchY)
    {
        int xDelta = Math.abs(touchX - originalTouchX);
        int yDelta = Math.abs(touchY - originalTouchY);
        return xDelta <= TAP_MOVEMENT_THRESHOLD &&
                yDelta <= TAP_MOVEMENT_THRESHOLD;
    }

    private boolean isTap()
    {
        if (confirmedDrag || confirmedMove || confirmedScroll) {
            return false;
        }

        // If this input wasn't the last finger down, do not report
        // a tap. This ensures we don't report duplicate taps for each
        // finger on a multi-finger tap gesture
        if (actionIndex + 1 != maxPointerCountInGesture) {
            return false;
        }

        long timeDelta = SystemClock.uptimeMillis() - originalTouchTime;
        return isWithinTapBounds(lastTouchX, lastTouchY) && timeDelta <= TAP_TIME_THRESHOLD;
    }

    private byte getMouseButtonIndex()
    {
        if (actionIndex == 1) {
            return MouseButtonPacket.BUTTON_RIGHT;
        }
        else {
            return MouseButtonPacket.BUTTON_LEFT;
        }
    }

    @Override
    public boolean touchDownEvent(int eventX, int eventY, boolean isNewFinger)
    {
        // Get the view dimensions to scale inputs on this touch
        xFactor = referenceWidth / (double)targetView.getWidth();
        yFactor = referenceHeight / (double)targetView.getHeight();

        originalTouchX = lastTouchX = eventX;
        originalTouchY = lastTouchY = eventY;

        if (isNewFinger) {
            maxPointerCountInGesture = pointerCount;
            originalTouchTime = SystemClock.uptimeMillis();
            cancelled = confirmedDrag = confirmedMove = confirmedScroll = false;
            distanceMoved = 0;

            if (actionIndex == 0) {
                // Start the timer for engaging a drag
                startDragTimer();
            }
        }

        return true;
    }

    @Override
    public void touchUpEvent(int eventX, int eventY)
    {
        if (cancelled) {
            return;
        }

        // Cancel the drag timer
        cancelDragTimer();

        byte buttonIndex = getMouseButtonIndex();

        if (confirmedDrag) {
            // Raise the button after a drag
            conn.sendMouseButtonUp(buttonIndex);
        }
        else if (isTap())
        {
            // Lower the mouse button
            conn.sendMouseButtonDown(buttonIndex);

            // We need to sleep a bit here because some games
            // do input detection by polling
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}

            // Raise the mouse button
            conn.sendMouseButtonUp(buttonIndex);
        }
    }

    private synchronized void startDragTimer() {
        // Cancel any existing drag timers
        cancelDragTimer();

        dragTimer = new Timer(true);
        dragTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (RelativeTouchContext.this) {
                    // Check if someone already set move
                    if (confirmedMove) {
                        return;
                    }

                    // The drag should only be processed for the primary finger
                    if (actionIndex != maxPointerCountInGesture - 1) {
                        return;
                    }

                    // Check if someone cancelled us
                    if (dragTimer == null) {
                        return;
                    }

                    // Uncancellable now
                    dragTimer = null;

                    // We haven't been cancelled before the timer expired so begin dragging
                    confirmedDrag = true;
                    conn.sendMouseButtonDown(getMouseButtonIndex());
                }
            }
        }, DRAG_TIME_THRESHOLD);
    }

    private synchronized void cancelDragTimer() {
        if (dragTimer != null) {
            dragTimer.cancel();
            dragTimer = null;
        }
    }

    private synchronized void checkForConfirmedMove(int eventX, int eventY) {
        // If we've already confirmed something, get out now
        if (confirmedMove || confirmedDrag) {
            return;
        }

        // If it leaves the tap bounds before the drag time expires, it's a move.
        if (!isWithinTapBounds(eventX, eventY)) {
            confirmedMove = true;
            cancelDragTimer();
            return;
        }

        // Check if we've exceeded the maximum distance moved
        distanceMoved += Math.sqrt(Math.pow(eventX - lastTouchX, 2) + Math.pow(eventY - lastTouchY, 2));
        if (distanceMoved >= TAP_DISTANCE_THRESHOLD) {
            confirmedMove = true;
            cancelDragTimer();
            return;
        }
    }

    private void checkForConfirmedScroll() {
        // Enter scrolling mode if we've already left the tap zone
        // and we have 2 fingers on screen. Leave scroll mode if
        // we no longer have 2 fingers on screen
        confirmedScroll = (actionIndex == 0 && pointerCount == 2 && confirmedMove);
    }

    @Override
    public boolean touchMoveEvent(int eventX, int eventY)
    {
        if (cancelled) {
            return true;
        }

        if (eventX != lastTouchX || eventY != lastTouchY)
        {
            checkForConfirmedMove(eventX, eventY);
            checkForConfirmedScroll();

            // We only send moves and drags for the primary touch point
            if (actionIndex == 0) {
                int deltaX = eventX - lastTouchX;
                int deltaY = eventY - lastTouchY;

                // Scale the deltas based on the factors passed to our constructor
                deltaX = (int) Math.round((double) Math.abs(deltaX) * xFactor);
                deltaY = (int) Math.round((double) Math.abs(deltaY) * yFactor);

                // Fix up the signs
                if (eventX < lastTouchX) {
                    deltaX = -deltaX;
                }
                if (eventY < lastTouchY) {
                    deltaY = -deltaY;
                }

                if (pointerCount == 2) {
                    if (confirmedScroll) {
                        conn.sendMouseHighResScroll((short)(deltaY * SCROLL_SPEED_FACTOR));
                    }
                } else {
                    conn.sendMouseMove((short) deltaX, (short) deltaY);
                }

                // If the scaling factor ended up rounding deltas to zero, wait until they are
                // non-zero to update lastTouch that way devices that report small touch events often
                // will work correctly
                if (deltaX != 0) {
                    lastTouchX = eventX;
                }
                if (deltaY != 0) {
                    lastTouchY = eventY;
                }
            }
            else {
                lastTouchX = eventX;
                lastTouchY = eventY;
            }
        }

        return true;
    }

    @Override
    public void cancelTouch() {
        cancelled = true;

        // Cancel the drag timer
        cancelDragTimer();

        // If it was a confirmed drag, we'll need to raise the button now
        if (confirmedDrag) {
            conn.sendMouseButtonUp(getMouseButtonIndex());
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setPointerCount(int pointerCount) {
        this.pointerCount = pointerCount;

        if (pointerCount > maxPointerCountInGesture) {
            maxPointerCountInGesture = pointerCount;
        }
    }
}
