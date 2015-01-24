package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Karim Mreisi on 23.01.2015.
 */
public class DigitalPad extends View
{
    public final  static int DIGITAL_PAD_DIRECTION_NO_DIRECTION        = 0;
    public final  static int DIGITAL_PAD_DIRECTION_LEFT                = 1;
    public final  static int DIGITAL_PAD_DIRECTION_UP                  = 2;
    public final  static int DIGITAL_PAD_DIRECTION_RIGHT               = 4;
    public final  static int DIGITAL_PAD_DIRECTION_DOWN                = 8;

    private int     normalColor  = 0xF0888888;
    private int     pressedColor  = 0xF00000FF;

    private  static final boolean _PRINT_DEBUG_INFORMATION = false;

    public interface DigitalPadListener
    {
        void onDirectionChange(int direction);
    }

    public void addDigitalPadListener (DigitalPadListener listener)
    {
        listeners.add(listener);
    }

    public void setOnTouchListener(OnTouchListener listener)
    {
        onTouchListener = listener;
    }

    private static final void _DBG(String text)
    {
        if (_PRINT_DEBUG_INFORMATION)
        {
            System.out.println("DigitalPad: " + text);
        }
    }

    List<DigitalPadListener> listeners		= new ArrayList<DigitalPadListener>();
    OnTouchListener                         onTouchListener = null;

    int                      direction;

    public DigitalPad(Context context)
    {
        super(context);

        direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
    }

    private float getPercent(float value, float percent)
    {
        return  value / 100 * percent;
    }

    private int getCorrectWidth()
    {
        return  getWidth() > getHeight() ? getHeight() : getWidth();
    }

    public  void setColors(int normalColor, int pressedColor)
    {
        this.normalColor    = normalColor;
        this.pressedColor   = pressedColor;
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        Paint paint = new Paint();

        paint.setTextSize(getPercent(getCorrectWidth(), 20));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(3);

        if (direction == DIGITAL_PAD_DIRECTION_NO_DIRECTION)
        {
            // draw no direction rect
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(normalColor);
            canvas.drawRect(
                    getPercent(getWidth(), 36), getPercent(getHeight(), 36),
                    getPercent(getWidth(), 63), getPercent(getHeight(), 63),
                    paint
            );
        }

        // draw left rect
        paint.setColor((direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 ? pressedColor : normalColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawText("LF",
                getPercent(getWidth(), 16.5f),  getPercent(getHeight(), 58),
                paint);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                0,                          getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                paint
        );

        // draw left up line
        paint.setColor((
                (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 &&
                (direction & DIGITAL_PAD_DIRECTION_UP) > 0
                    )  ? pressedColor : normalColor
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                0,                          getPercent(getWidth(), 33),
                getPercent(getWidth(), 33), 0,
                paint
        );

        // draw up rect
        paint.setColor((direction & DIGITAL_PAD_DIRECTION_UP) > 0 ? pressedColor : normalColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawText("UP",
                getPercent(getWidth(), 49.5f),  getPercent(getHeight(), 25),
                paint);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 33),                          0,
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                paint
        );

        // draw up right line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_UP) > 0 &&
                        (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0
                )  ? pressedColor : normalColor
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getPercent(getWidth(), 66),                          0,
                getPercent(getWidth(), 100), getPercent(getHeight(), 33),
                paint
        );

        // draw right rect
        paint.setColor((direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 ? pressedColor : normalColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawText("RI",
                getPercent(getWidth(), 82.5f),  getPercent(getHeight(), 58),
                paint);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                getPercent(getWidth(), 100), getPercent(getHeight(), 66),
                paint
        );

        // draw right down line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 &&
                        (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0
                )  ? pressedColor : normalColor
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getPercent(getWidth(), 100), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getPercent(getHeight(), 100),
                paint
        );

        // draw down rect
        paint.setColor((direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 ? pressedColor : normalColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawText("DW",
                getPercent(getWidth(), 49.5f),  getPercent(getHeight(), 91),
                paint);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getPercent(getHeight(), 100),
                paint
        );

        // draw down left line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 &&
                        (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0
                )  ? pressedColor : normalColor
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getPercent(getWidth(), 33), getPercent(getHeight(), 100),
                getPercent(getWidth(), 0),  getPercent(getHeight(), 66),
                paint
        );

        super.onDraw(canvas);
    }

    private void newDirectionCallback(int direction)
    {
        _DBG("direction: " + direction);

        // notify listeners
        for (DigitalPadListener listener : listeners)
        {
            listener.onDirectionChange(direction);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        if (onTouchListener != null)
        {
            return onTouchListener.onTouch(this, event);
        }

        // get masked (not specific to a pointer) action
        int action = event.getActionMasked();

        switch (action)
        {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            {
                direction = 0;

                if (event.getX() < getPercent(getWidth(), 33))
                {
                    direction |= DIGITAL_PAD_DIRECTION_LEFT;
                }

                if (event.getX() > getPercent(getWidth(), 66))
                {
                    direction |= DIGITAL_PAD_DIRECTION_RIGHT;
                }

                if (event.getY() > getPercent(getHeight(), 66))
                {
                    direction |= DIGITAL_PAD_DIRECTION_DOWN;
                }

                if (event.getY() < getPercent(getHeight(), 33))
                {
                    direction |= DIGITAL_PAD_DIRECTION_UP;
                }

                newDirectionCallback(direction);

                invalidate();

                return  true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            {
                direction = 0;

                newDirectionCallback(direction);

                invalidate();

                return true;
            }
            default:
            {
            }
        }

        return super.onTouchEvent(event);
    }
}
