package com.limelight.binding.input;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;

public class TouchContext {
    private int lastTouchX = 0;
    private int lastTouchY = 0;
    private int originalTouchX = 0;
    private int originalTouchY = 0;
    private long originalTouchTime = 0;
    private boolean cancelled;

    private final NvConnection conn;
    private final int actionIndex;
    private final double xFactor;
    private final double yFactor;

    private static final int TAP_MOVEMENT_THRESHOLD = 10;
    private static final int TAP_TIME_THRESHOLD = 250;

    public TouchContext(NvConnection conn, int actionIndex, double xFactor, double yFactor)
    {
        this.conn = conn;
        this.actionIndex = actionIndex;
        this.xFactor = xFactor;
        this.yFactor = yFactor;
    }

    public int getActionIndex()
    {
        return actionIndex;
    }

    private boolean isTap()
    {
        int xDelta = Math.abs(lastTouchX - originalTouchX);
        int yDelta = Math.abs(lastTouchY - originalTouchY);
        long timeDelta = System.currentTimeMillis() - originalTouchTime;

        return xDelta <= TAP_MOVEMENT_THRESHOLD &&
                yDelta <= TAP_MOVEMENT_THRESHOLD &&
                timeDelta <= TAP_TIME_THRESHOLD;
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

    public boolean touchDownEvent(int eventX, int eventY)
    {
        originalTouchX = lastTouchX = eventX;
        originalTouchY = lastTouchY = eventY;
        originalTouchTime = System.currentTimeMillis();
        cancelled = false;

        return true;
    }

    public void touchUpEvent(int eventX, int eventY)
    {
        if (cancelled) {
            return;
        }

        if (isTap())
        {
            byte buttonIndex = getMouseButtonIndex();

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

    public boolean touchMoveEvent(int eventX, int eventY)
    {
        if (eventX != lastTouchX || eventY != lastTouchY)
        {
            // We only send moves for the primary touch point
            if (actionIndex == 0) {
                int deltaX = eventX - lastTouchX;
                int deltaY = eventY - lastTouchY;

                // Scale the deltas based on the factors passed to our constructor
                deltaX = (int)Math.round((double)deltaX * xFactor);
                deltaY = (int)Math.round((double)deltaY * yFactor);

                conn.sendMouseMove((short)deltaX, (short)deltaY);
            }

            lastTouchX = eventX;
            lastTouchY = eventY;
        }

        return true;
    }

    public void cancelTouch() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
