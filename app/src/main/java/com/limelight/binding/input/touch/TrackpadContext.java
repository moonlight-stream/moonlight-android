package com.limelight.binding.input.touch;

import android.os.Handler;
import android.os.Looper;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;

public class TrackpadContext implements TouchContext {
    private int lastTouchX = 0;
    private int lastTouchY = 0;
    private int originalTouchX = 0;
    private int originalTouchY = 0;
    private long originalTouchTime = 0;
    private boolean cancelled;
    private boolean confirmedMove;
    private boolean confirmedDrag;
    private boolean confirmedScroll;
    private double distanceMoved;
    private int pointerCount;
    private int maxPointerCountInGesture;
    private boolean isClickPending;
    private boolean isDblClickPending;

    private final NvConnection conn;
    private final int actionIndex;
    private final Handler handler;

    private static final int TAP_MOVEMENT_THRESHOLD = 20;
    private static final int TAP_DISTANCE_THRESHOLD = 25;
    private static final int TAP_TIME_THRESHOLD = 230;
    private static final int CLICK_RELEASE_DELAY = TAP_TIME_THRESHOLD;
    private static final int SCROLL_SPEED_FACTOR_X = 3;
    private static final int SCROLL_SPEED_FACTOR_Y = 5;

    // Indexed by MouseButtonPacket.BUTTON_XXX - 1
    private final Runnable[] buttonUpRunnables = new Runnable[] {
            new Runnable() {
                @Override
                public void run() {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE);
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1);
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2);
                }
            }
    };

    public TrackpadContext(NvConnection conn, int actionIndex) {
        this.conn = conn;
        this.actionIndex = actionIndex;
        this.handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int getActionIndex() {
        return actionIndex;
    }

    private boolean isWithinTapBounds(int touchX, int touchY) {
        int xDelta = Math.abs(touchX - originalTouchX);
        int yDelta = Math.abs(touchY - originalTouchY);
        return xDelta <= TAP_MOVEMENT_THRESHOLD && yDelta <= TAP_MOVEMENT_THRESHOLD;
    }

    private boolean isTap(long eventTime) {
        if (confirmedDrag || confirmedMove || confirmedScroll) {
            return false;
        }

        if (actionIndex + 1 != maxPointerCountInGesture) {
            return false;
        }

        long timeDelta = eventTime - originalTouchTime;
        return isWithinTapBounds(lastTouchX, lastTouchY) && timeDelta <= TAP_TIME_THRESHOLD;
    }

    private byte getMouseButtonIndex() {
        if (pointerCount == 2) {
            return MouseButtonPacket.BUTTON_RIGHT;
        } else {
            return MouseButtonPacket.BUTTON_LEFT;
        }
    }

    @Override
    public boolean touchDownEvent(int eventX, int eventY, long eventTime, boolean isNewFinger) {
        originalTouchX = lastTouchX = eventX;
        originalTouchY = lastTouchY = eventY;

        if (isNewFinger) {
            maxPointerCountInGesture = pointerCount;
            originalTouchTime = eventTime;
            cancelled = confirmedMove = confirmedScroll = false;
            distanceMoved = 0;
        }

        if (isClickPending) {
            isClickPending = false;
            isDblClickPending = true;
            confirmedDrag = true;
        }

        return true;
    }

    @Override
    public void touchUpEvent(int eventX, int eventY, long eventTime) {
        if (cancelled) {
            return;
        }

        byte buttonIndex = getMouseButtonIndex();

        if (isDblClickPending) {
            conn.sendMouseButtonUp(buttonIndex);
            conn.sendMouseButtonDown(buttonIndex);
            conn.sendMouseButtonUp(buttonIndex);
            isClickPending = false;
            confirmedDrag = false;
        }
        else if (confirmedDrag) {
            conn.sendMouseButtonUp(buttonIndex);
            confirmedDrag = false;
        }
        else if (isTap(eventTime)) {
            conn.sendMouseButtonDown(buttonIndex);
            isClickPending = true;

            Runnable buttonUpRunnable = buttonUpRunnables[buttonIndex - 1];
            handler.removeCallbacks(buttonUpRunnable);
            handler.postDelayed(() -> {
                if (isClickPending) {
                    conn.sendMouseButtonUp(buttonIndex);
                    isClickPending = false;
                }
                isDblClickPending = false;
            }, CLICK_RELEASE_DELAY);
        }
    }

    @Override
    public boolean touchMoveEvent(int eventX, int eventY, long eventTime) {
        if (cancelled) {
            return true;
        }

        if (eventX != lastTouchX || eventY != lastTouchY) {
            checkForConfirmedMove(eventX, eventY);
            checkForConfirmedScroll();

            if (isDblClickPending) {
                isDblClickPending = false;
                confirmedDrag = true;
            }

            // We only send moves and drags for the primary touch point
            if (actionIndex == 0) {
                int absDeltaX = Math.abs(eventX - lastTouchX);
                int absDeltaY = Math.abs(eventY - lastTouchY);

                int deltaX = absDeltaX;
                int deltaY = absDeltaY;

                // Fix up the signs
                if (eventX < lastTouchX) {
                    deltaX = -absDeltaX;
                }
                if (eventY < lastTouchY) {
                    deltaY = -absDeltaY;
                }

                if (pointerCount == 2) {
                    if (confirmedScroll) {
                        if (absDeltaX > absDeltaY) {
                            conn.sendMouseHighResHScroll((short)(-deltaX * SCROLL_SPEED_FACTOR_X));
                            if (absDeltaY * 1.05 > absDeltaX) {
                                conn.sendMouseHighResScroll((short)(deltaY * SCROLL_SPEED_FACTOR_Y));
                            }
                        } else {
                            conn.sendMouseHighResScroll((short)(deltaY * SCROLL_SPEED_FACTOR_Y));
                            if (absDeltaX * 1.05 >= absDeltaY) {
                                conn.sendMouseHighResHScroll((short)(-deltaX * SCROLL_SPEED_FACTOR_X));
                            }
                        }
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

    private void checkForConfirmedMove(int eventX, int eventY) {
        // If we've already confirmed something, get out now
        if (confirmedMove || confirmedDrag) {
            return;
        }

        // If it leaves the tap bounds before the drag time expires, it's a move.
        if (!isWithinTapBounds(eventX, eventY)) {
            confirmedMove = true;
            return;
        }

        // Check if we've exceeded the maximum distance moved
        distanceMoved += Math.sqrt(Math.pow(eventX - lastTouchX, 2) + Math.pow(eventY - lastTouchY, 2));
        if (distanceMoved >= TAP_DISTANCE_THRESHOLD) {
            confirmedMove = true;
        }
    }

    private void checkForConfirmedScroll() {
        confirmedScroll = (actionIndex == 0 && pointerCount == 2 && confirmedMove);
    }
}
