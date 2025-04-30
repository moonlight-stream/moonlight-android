package com.limelight.utils;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.limelight.Game;
import com.limelight.preferences.PreferenceConfiguration;

public class PanZoomHandler {
    static private final float MAX_SCALE = 10.0f;

    private final Game game;
    private final View streamView;
    private final PreferenceConfiguration prefConfig;
    private final boolean isTopMode;
    private final ScaleGestureDetector scaleGestureDetector;
    private final GestureDetector gestureDetector;
    private View parent;
    private float scaleFactor = 1.0f;
    private float childX, childY = 0;
    private float parentWidth, parentHeight = 0;
    private float childWidth, childHeight = 0;

    public PanZoomHandler(Context context, Game game, View streamView, PreferenceConfiguration prefConfig) {
        this.game = game;
        this.streamView = streamView;
        this.prefConfig = prefConfig;
        this.isTopMode = prefConfig.alignDisplayTopCenter;
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());

        // Everything gets easier with 0,0 as the pivot point
        streamView.setPivotX(0);
        streamView.setPivotY(0);
    }

    public void handleTouchEvent(MotionEvent motionEvent) {
        scaleGestureDetector.onTouchEvent(motionEvent);
        gestureDetector.onTouchEvent(motionEvent);
    }

    private void updateDimensions() {
        childHeight = streamView.getHeight() * scaleFactor;
        childWidth = streamView.getWidth() * scaleFactor;
        parentWidth = parent.getWidth();
        parentHeight = parent.getHeight();
    }

    private void constrainToBounds() {
        updateDimensions();

        if (parentWidth >= childWidth) {
            childX = (parentWidth - childWidth) / 2;
        } else {
            float boundaryX = parentWidth - childWidth;
            childX = Math.max(boundaryX, Math.min(childX, 0));
        }

        if (parentHeight >= childHeight) {
            if (isTopMode) {
                childY = 0;
            } else {
                childY = (parentHeight - childHeight) / 2;
            }
        } else {
            float boundaryY = parentHeight - childHeight;
            childY = Math.max(boundaryY, Math.min(childY, 0));
        }

        streamView.setX(childX);
        streamView.setY(childY);
    }

    public void handleSurfaceChange() {
        if (childWidth == 0 || parent == null) {
            // Retrieve parent, should handle both built-in display and external display
            parent = (View)streamView.getParent();
            return;
        }

        float prevChildWidth = childWidth;
        float prevChildHeight = childHeight;
        float prevParentWidth = parentWidth;
        float prevParentHeight = parentHeight;

        updateDimensions();

        float viewScaleX = childWidth / prevChildWidth;
        float viewScaleY = childHeight / prevChildHeight;

        float dPivotX1 = childX - prevParentWidth / 2;
        float dPivotY1 = childY - prevParentHeight / 2;

        float dPivotX2 = dPivotX1 * viewScaleX;
        float dPivotY2 = dPivotY1 * viewScaleY;

        childX = dPivotX2 + parentWidth / 2;
        childY = dPivotY2 + parentHeight / 2;

        streamView.setX(childX);
        streamView.setY(childY);

        constrainToBounds();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float newScaleFactor = scaleFactor * detector.getScaleFactor();
            newScaleFactor = Math.max(1, Math.min(newScaleFactor, MAX_SCALE)); // Apply minimum scale

            // Calculate pivot point
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();

            float dPivotX = (childX - focusX) / scaleFactor * newScaleFactor;
            float dPivotY = (childY - focusY) / scaleFactor * newScaleFactor;

            childX = focusX + dPivotX;
            childY = focusY + dPivotY;

            scaleFactor = newScaleFactor;

            streamView.setScaleX(scaleFactor);
            streamView.setScaleY(scaleFactor);

            streamView.setX(childX);
            streamView.setY(childY);

            constrainToBounds();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            game.updatePipAutoEnter();
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            childX = streamView.getX() - distanceX;
            childY = streamView.getY() - distanceY;

            streamView.setX(childX);
            streamView.setY(childY);

            constrainToBounds();
            return true;
        }
    }
}
