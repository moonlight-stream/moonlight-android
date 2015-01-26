package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Karim on 24.01.2015.
 */
public class DigitalButton  extends View
{
    private class TimerLongClickTimerTask extends TimerTask
    {
        @Override
        public void run()
        {
            onLongClickCallback();
        }
    }
    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private int     normalColor     = 0xF0888888;
    private int     pressedColor    = 0xF00000FF;
    private String  text            = "";
    private int     icon            = -1;

    private long                    timerLongClickTimeout   = 3000;
    private Timer                   timerLongClick          = null;
    private TimerLongClickTimerTask longClickTimerTask      = null;


    public interface DigitalButtonListener
    {
        void onClick();
        void onLongClick();
        void onRelease();
    }

    public void addDigitalButtonListener(DigitalButtonListener listener)
    {
        listeners.add(listener);
    }

    public  void setColors(int normalColor, int pressedColor)
    {
        this.normalColor    = normalColor;
        this.pressedColor   = pressedColor;
    }

    public void setOnTouchListener(OnTouchListener listener)
    {
        onTouchListener = listener;
    }

    private static final void _DBG(String text)
    {
        if (_PRINT_DEBUG_INFORMATION)
        {
            System.out.println("DigitalButton: " + text);
        }
    }

    List<DigitalButtonListener> listeners = new ArrayList<DigitalButtonListener>();
    OnTouchListener onTouchListener = null;

    boolean clicked;

    public DigitalButton(Context context)
    {
        super(context);

        clicked = false;
    }

    public void setText(String text)
    {
        this.text = text;

        invalidate();
    }

    public  void setIcon(int id)
    {
        this.icon = id;

        invalidate();
    }

    private float getPercent(float value, float percent)
    {
        return value / 100 * percent;
    }

    private int getCorrectWidth()
    {
        return getWidth() > getHeight() ? getHeight() : getWidth();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        Paint paint = new Paint();

        paint.setTextSize(getPercent(getCorrectWidth(), 50));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(3);

        paint.setColor(clicked ? pressedColor : normalColor);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                1,              1,
                getWidth() - 1, getHeight() - 1,
                paint
        );

        if (icon != -1)
        {
            Drawable d = getResources().getDrawable(icon);
            d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
            d.draw(canvas);
        }
        else
        {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            canvas.drawText(text,
                    getPercent(getWidth(), 50), getPercent(getHeight(), 73),
                    paint);
        }

        super.onDraw(canvas);
    }

    private void onClickCallback()
    {
        _DBG("clicked");

        // notify listeners
        for (DigitalButtonListener listener : listeners)
        {
            listener.onClick();
        }

        timerLongClick          = new Timer();
        longClickTimerTask      = new TimerLongClickTimerTask();

        timerLongClick.schedule(longClickTimerTask, timerLongClickTimeout);
    }

    private void onLongClickCallback()
    {
        _DBG("long click");

        // notify listeners
        for (DigitalButtonListener listener : listeners)
        {
            listener.onLongClick();
        }
    }

    private void onReleaseCallback()
    {
        _DBG("released");

        // notify listeners
        for (DigitalButtonListener listener : listeners)
        {
            listener.onRelease();
        }

        timerLongClick.cancel();
        longClickTimerTask.cancel();
    }


    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        /*
        if (onTouchListener != null)
        {
            return onTouchListener.onTouch(this, event);
        }
        */
        // get masked (not specific to a pointer) action
        int action = event.getActionMasked();

        switch (action)
        {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            {
                clicked = true;
                onClickCallback();

                invalidate();

                return  true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            {
                clicked = false;
                onReleaseCallback();

                invalidate();

                return true;
            }
            default:
            {
            }
        }

        return true;
    }
}
