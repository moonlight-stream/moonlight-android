/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class DigitalPad extends VirtualControllerElement {
    public final static int DIGITAL_PAD_DIRECTION_NO_DIRECTION = 0;
    int direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
    public final static int DIGITAL_PAD_DIRECTION_LEFT = 1;
    public final static int DIGITAL_PAD_DIRECTION_UP = 2;
    public final static int DIGITAL_PAD_DIRECTION_RIGHT = 4;
    public final static int DIGITAL_PAD_DIRECTION_DOWN = 8;
    List<DigitalPadListener> listeners = new ArrayList<>();

    private static final int DPAD_MARGIN = 5;
    private final RectF rect = new RectF();

    private final Paint paint = new Paint();

    public DigitalPad(VirtualController controller, Context context) {
        super(controller, context, EID_DPAD);
    }

    public void addDigitalPadListener(DigitalPadListener listener) {
        listeners.add(listener);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getCorrectWidth(), 20));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        //虚拟手柄皮肤 yuzu
        if(!PreferenceConfiguration.readPreferences(getContext()).enableOnScreenStyleOfficial) {
            int oscOpacity=PreferenceConfiguration.readPreferences(getContext()).oscOpacity;

            paint.setColor(isPressed() ? pressedColor:getDefaultColor());
            rect.left = rect.top = paint.getStrokeWidth();
            rect.right = getWidth() - rect.left;
            rect.bottom = getHeight() - rect.top;

            boolean bIsMoving = virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons;
            boolean bIsResizing = virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons;
            boolean bIsEnable = virtualController.getControllerMode() == VirtualController.ControllerMode.DisableEnableButtons;

            if (bIsMoving || bIsResizing || bIsEnable) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(rect,paint);
            }

            if (direction == DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
                Drawable d = getResources().getDrawable(R.drawable.facebutton_dpad);
                d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
                d.setAlpha((int) (oscOpacity*2.55));
                d.draw(canvas);
            }

            if (direction == DIGITAL_PAD_DIRECTION_UP) {
                Drawable d = getResources().getDrawable(R.drawable.facebutton_dpad_up);
                d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
                d.setAlpha((int) (oscOpacity*2.55));
                d.draw(canvas);
            }

            if (direction == DIGITAL_PAD_DIRECTION_DOWN) {
                Drawable d = getResources().getDrawable(R.drawable.facebutton_dpad_up);
                Drawable newD=rotateDrawable(d,180);
                newD.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
                newD.setAlpha((int) (oscOpacity*2.55));
                newD.draw(canvas);
            }

            if (direction == DIGITAL_PAD_DIRECTION_LEFT) {
                Drawable d = getResources().getDrawable(R.drawable.facebutton_dpad_up);
                Drawable newD=rotateDrawable(d,270);
                newD.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
                newD.setAlpha((int) (oscOpacity*2.55));
                newD.draw(canvas);
            }

            if (direction == DIGITAL_PAD_DIRECTION_RIGHT) {
                Drawable d = getResources().getDrawable(R.drawable.facebutton_dpad_up);
                Drawable newD=rotateDrawable(d,90);
                newD.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
                newD.setAlpha((int) (oscOpacity*2.55));
                newD.draw(canvas);
            }
            //right up
            if((direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 && (direction & DIGITAL_PAD_DIRECTION_UP) > 0){
                Drawable d = getResources().getDrawable(R.drawable.facebutton_dpad_up_right);
                Drawable newD=rotateDrawable(d,90);
                newD.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
                newD.setAlpha((int) (oscOpacity*2.55));
                newD.draw(canvas);
            }

            if((direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 && (direction & DIGITAL_PAD_DIRECTION_UP) > 0){
                Drawable d = getResources().getDrawable(R.drawable.facebutton_dpad_up_right);
                d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
                d.setAlpha((int) (oscOpacity*2.55));
                d.draw(canvas);
            }

            if((direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 && (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0){
                Drawable d = getResources().getDrawable(R.drawable.facebutton_dpad_up_right);
                Drawable newD=rotateDrawable(d,180);
                newD.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
                newD.setAlpha((int) (oscOpacity*2.55));
                newD.draw(canvas);
            }

            if((direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 && (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0){
                Drawable d = getResources().getDrawable(R.drawable.facebutton_dpad_up_right);
                Drawable newD=rotateDrawable(d,270);
                newD.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
                newD.setAlpha((int) (oscOpacity*2.55));
                newD.draw(canvas);
            }

            return;
        }
        //官方皮肤
        if (direction == DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
            // draw no direction rect
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(getDefaultColor());
            canvas.drawRect(
                    getPercent(getWidth(), 36), getPercent(getHeight(), 36),
                    getPercent(getWidth(), 63), getPercent(getHeight(), 63),
                    paint
            );
        }

        // draw left rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                paint
        );


        // draw up rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_UP) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 33), paint.getStrokeWidth()+DPAD_MARGIN,
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                paint
        );

        // draw right rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                getWidth() - (paint.getStrokeWidth()+DPAD_MARGIN), getPercent(getHeight(), 66),
                paint
        );

        // draw down rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight() - (paint.getStrokeWidth()+DPAD_MARGIN),
                paint
        );

        // draw left up line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_UP) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), paint.getStrokeWidth()+DPAD_MARGIN,
                paint
        );

        // draw up right line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_UP) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getPercent(getWidth(), 66), paint.getStrokeWidth()+DPAD_MARGIN,
                getWidth() - (paint.getStrokeWidth()+DPAD_MARGIN), getPercent(getHeight(), 33),
                paint
        );

        // draw right down line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getWidth()-paint.getStrokeWidth(), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight()-(paint.getStrokeWidth()+DPAD_MARGIN),
                paint
        );

        // draw down left line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getPercent(getWidth(), 33), getHeight()-(paint.getStrokeWidth()+DPAD_MARGIN),
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 66),
                paint
        );
    }

    public Drawable rotateDrawable(Drawable vectorDrawable, float angle) {
        int width = vectorDrawable.getIntrinsicWidth();
        int height = vectorDrawable.getIntrinsicHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.draw(canvas);

        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return new BitmapDrawable(getResources(), rotatedBitmap);
    }

    private void newDirectionCallback(int direction) {
        _DBG("direction: " + direction);

        // notify listeners
        for (DigitalPadListener listener : listeners) {
            listener.onDirectionChange(direction);
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                direction = 0;

                if (event.getX() < getPercent(getWidth(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_LEFT;
                }
                if (event.getX() > getPercent(getWidth(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_RIGHT;
                }
                if (event.getY() > getPercent(getHeight(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_DOWN;
                }
                if (event.getY() < getPercent(getHeight(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_UP;
                }
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                direction = 0;
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            default: {
            }
        }

        return true;
    }

    public interface DigitalPadListener {
        void onDirectionChange(int direction);
    }
}
