/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.MotionEvent;

import com.limelight.LimeLog;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 */
public class KeyBoardTouchPadButton extends keyBoardVirtualControllerElement {

    /**
     * Listener interface to update registered observers.
     */
    public interface DigitalButtonListener {

        /**
         * onClick event will be fired on button click.
         */
        void onClick();

        /**
         * onLongClick event will be fired on button long click.
         */
        void onLongClick();

        void onMove(int x, int y);

        /**
         * onRelease event will be fired on button unpress.
         */
        void onRelease();
    }

    private List<DigitalButtonListener> listeners = new ArrayList<>();
    private String text = "";
    private int icon = -1;
    private long timerLongClickTimeout = 3000;
    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            onLongClickCallback();
        }
    };

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();

    private int layer;
    private KeyBoardTouchPadButton movingButton = null;

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public boolean checkMovement(float x, float y, KeyBoardTouchPadButton movingButton) {
        // check if the movement happened in the same layer
        if (movingButton.layer != this.layer) {
            return false;
        }

        // save current pressed state
        boolean wasPressed = isPressed();

        // check if the movement directly happened on the button
        if ((this.movingButton == null || movingButton == this.movingButton)
                && this.inRange(x, y)) {
            // set button pressed state depending on moving button pressed state
            if (this.isPressed() != movingButton.isPressed()) {
                this.setPressed(movingButton.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton == this.movingButton) {
            this.setPressed(false);
        }

        // check if a change occurred
        if (wasPressed != isPressed()) {
            if (isPressed()) {
                // is pressed set moving button and emit click event
                this.movingButton = movingButton;
                onClickCallback();
            } else {
                // no longer pressed reset moving button and emit release event
                this.movingButton = null;
                onReleaseCallback();
            }

            invalidate();

            return true;
        }

        return false;
    }

    private void checkMovementForAllButtons(float x, float y) {
        for (keyBoardVirtualControllerElement element : virtualController.getElements()) {
            if (element != this && element instanceof KeyBoardTouchPadButton) {
                ((KeyBoardTouchPadButton) element).checkMovement(x, y, this);
            }
        }
    }

    public KeyBoardTouchPadButton(KeyBoardController controller, String elementId, int layer, Context context) {
        super(controller, context, elementId);
        this.layer = layer;
        preferenceConfiguration=PreferenceConfiguration.readPreferences(context);
    }

    public void addDigitalButtonListener(DigitalButtonListener listener) {
        listeners.add(listener);
    }

    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    public void setIcon(int id) {
        this.icon = id;
        invalidate();
    }

    int pressedColor = 0x2BF5F5F9;

    PreferenceConfiguration preferenceConfiguration;

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getWidth(), 25));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        paint.setColor(isPressed() ? pressedColor : getDefaultColor());

        paint.setStyle(isPressed() ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);

        rect.left = rect.top = paint.getStrokeWidth();
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;

        canvas.drawRect(rect, paint);

        if (icon != -1) {
            Drawable d = getResources().getDrawable(icon);
            d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
            d.draw(canvas);
        } else {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth() / 2);
            canvas.drawText(text, getPercent(getWidth(), 50), getPercent(getHeight(), 63), paint);
        }
    }

    private void onClickCallback() {
        _DBG("clicked");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onClick();
        }

        virtualController.getHandler().removeCallbacks(longClickRunnable);
        virtualController.getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void onLongClickCallback() {
        _DBG("long click");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onLongClick();
        }
    }

    private void onMoveCallback(int x, int y) {
        _DBG("long click");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onMove(x, y);
        }
    }

    private void onReleaseCallback() {
        _DBG("released");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onRelease();
        }

        // We may be called for a release without a prior click
        virtualController.getHandler().removeCallbacks(longClickRunnable);
    }

    private long originalTouchTime = 0;
    private int lastTouchX = 0;
    private int lastTouchY = 0;

    private double xFactor, yFactor;

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                xFactor = 1280 / (double) getWidth();
                yFactor = 720 / (double) getHeight();
                lastTouchX = (int) event.getX();
                lastTouchY = (int) event.getY();
                movingButton = null;
                originalTouchTime = event.getEventTime();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int deltaX = (int) (event.getX() - lastTouchX);
                int deltaY = (int) (event.getY() - lastTouchY);
                deltaX = (int) Math.round((double) Math.abs(deltaX) * xFactor);
                deltaY = (int) Math.round((double) Math.abs(deltaY) * yFactor);
                // Fix up the signs
                if (event.getX() < lastTouchX) {
                    deltaX = -deltaX;
                }
                if (event.getY() < lastTouchY) {
                    deltaY = -deltaY;
                }
                if (event.getEventTime() - originalTouchTime > 100 && !isPressed()) {
                    setPressed(true);
                    if(TextUtils.equals(elementId,"m_9")||TextUtils.equals(elementId,"m_11")){
                        onClickCallback();
                    }
                }
//                LimeLog.info("touchPadSensitivity"+preferenceConfiguration.touchPadSensitivity);
//                LimeLog.info("onElementTouchEvent:" + deltaX + "," + deltaY);
                onMoveCallback((int) (deltaX*0.01f*preferenceConfiguration.touchPadSensitivity), (int) (deltaY*0.01f*preferenceConfiguration.touchPadYSensitity));
                if (deltaX != 0) {
                    lastTouchX = (int) event.getX();
                }
                if (deltaY != 0) {
                    lastTouchY = (int) event.getY();
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                setPressed(false);
                if (event.getEventTime() - originalTouchTime <= 200) {
                    onClickCallback();
                }
                onReleaseCallback();
                invalidate();
                return true;
            }
            default: {
            }
        }
        return true;
    }
}
