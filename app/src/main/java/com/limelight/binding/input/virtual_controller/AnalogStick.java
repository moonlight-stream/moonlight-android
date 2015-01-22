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
 * Created by Karim Mreisi on 30.11.2014.
 */
public class AnalogStick extends View
{
	private enum _STICK_STATE
	{
		NO_MOVEMENT,
		MOVED
	}

	private  static final boolean _PRINT_DEBUG_INFORMATION = false;

	public interface AnalogStickListener
	{
		void onMovement(float x, float y);
	}

	public void addAnalogStickListener (AnalogStickListener listener)
	{
		listeners.add(listener);
	}

	private static final void _DBG(String text)
	{
		if (_PRINT_DEBUG_INFORMATION)
		{
			System.out.println("AnalogStick: " + text);
		}
	}

	float 		radius_complete			= 0;
	float 		radius_dead_zone		= 0;
	float 		radius_analog_stick		= 0;

	float 		position_stick_x		= 0;
	float		position_stick_y		= 0;

	boolean		pressed				= false;
	_STICK_STATE	stick_state			= _STICK_STATE.NO_MOVEMENT;

	List<AnalogStickListener> listeners		= new ArrayList<AnalogStickListener>();

	public AnalogStick(Context context)
	{
		super(context);

		position_stick_x	= getWidth() / 2;
		position_stick_y	= getHeight() / 2;

		stick_state		= _STICK_STATE.NO_MOVEMENT;
		pressed			= false;

	}

	private float getPercent(float value, int percent)
	{
		return  value / 100 * percent;
	}

	private int getCorrectWidth()
	{
		return  getWidth() > getHeight() ? getHeight() : getWidth();
	}

	private double getMovementRadius(float x, float y)
	{
		if (x == 0)
		{
			return y > 0 ? y : -y;
		}

		if (y == 0)
		{
			return x > 0 ? x : -x;
		}

		return  Math.sqrt(x * x + y * y);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		radius_complete		= getPercent(getCorrectWidth() / 2, 90);
		radius_dead_zone	= getPercent(getCorrectWidth() / 2, 10);
		radius_analog_stick	= getPercent(getCorrectWidth() / 2, 20);

		super.onSizeChanged(w, h, oldw, oldh);
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		Paint paint = new Paint();
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(getPercent(getCorrectWidth() / 2, 2));

		paint.setColor(Color.YELLOW);

		// draw outer circle
		canvas.drawCircle(getWidth() / 2, getHeight() / 2,  radius_complete, paint);

		// draw dead zone
		canvas.drawCircle(getWidth() / 2, getHeight() / 2,  radius_dead_zone, paint);

		// draw stick depending on state (no movement, moved, active(out of dead zone))
		if (pressed)
		{
			switch (stick_state)
			{
				case NO_MOVEMENT:
				{
					paint.setColor(Color.BLUE);
					canvas.drawCircle(position_stick_x, position_stick_y, radius_analog_stick, paint);

					break;
				}
				case MOVED:
				{
					paint.setColor(Color.CYAN);
					canvas.drawCircle(position_stick_x, position_stick_y, radius_analog_stick, paint);

					break;
				}
			}
		}
		else
		{
			paint.setColor(Color.RED);
			canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius_analog_stick, paint);
		}
		// set transparent background
		canvas.drawColor(Color.TRANSPARENT);

		super.onDraw(canvas);
	}

	private double getAngle(float way_x, float way_y)
	{
		double angle = 0;

		// prevent divisions by zero
		if (way_x == 0)
		{
			if (way_y > 0)
			{
				angle = 0;
			}
			else if (way_y < 0)
			{
				angle = Math.PI;
			}
		}
		else if (way_y == 0)
		{
			if (way_x > 0)
			{
				angle = Math.PI * 3/2;
			}
			else if (way_x < 0)
			{
				angle = Math.PI * 1/2;
			}
		}
		else
		{
			if (way_x > 0)
			{
				if (way_y < 0)
				{	// first quadrant
					angle = 3 * Math.PI / 2 + Math.atan((double)(-way_y / way_x));
				}
				else
				{	// second quadrant
					angle = Math.PI + Math.atan((double)(way_x / way_y));
				}
			}
			else
			{
				if (way_y > 0)
				{	// third quadrant
					angle = Math.PI / 2 + Math.atan((double)(way_y / -way_x));
				}
				else
				{	// fourth quadrant
					angle = 0 + Math.atan((double) (-way_x / -way_y));
				}
			}
		}

		_DBG("angle: " + angle + "  way y: "+ way_y + " way x: " + way_x);

		return  angle;
	}

	private void moveActionCallback(float x, float y)
	{
		_DBG("movement x: " + x + " movement y: " + y);

		// notify listeners
		for (AnalogStickListener listener : listeners)
		{
			listener.onMovement(x, y);
		}
	}

	private void updatePosition(float x, float y)
	{
		float way_x		= -(getWidth() / 2 - x);
		float way_y		= -(getHeight() / 2 - y);

		float movement_x	= 0;
		float movement_y	= 0;

		double movement_radius	= getMovementRadius(way_x, way_y);
		double movement_angle	= getAngle(way_x, way_y);

		// chop radius if out of outer circle
		if (movement_radius > (radius_complete - radius_analog_stick))
		{
			movement_radius = radius_complete - radius_analog_stick;
		}

		float correlated_y = (float)(Math.sin(Math.PI / 2 - movement_angle) * (movement_radius));
		float correlated_x = (float)(Math.cos(Math.PI / 2 - movement_angle) * (movement_radius));

		float complete = (radius_complete - radius_analog_stick);

		movement_x = -(1 / complete) * correlated_x;
		movement_y = (1 / complete) * correlated_y;

		position_stick_x = getWidth() / 2 - correlated_x;
		position_stick_y = getHeight() / 2 - correlated_y;

		// check if analog stick is inside of dead zone
		if (movement_radius > radius_dead_zone)
		{
			moveActionCallback(movement_x, movement_y);

			stick_state = _STICK_STATE.MOVED;
		}
		else
		{
			stick_state = _STICK_STATE.NO_MOVEMENT;
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		// get masked (not specific to a pointer) action
		int action = event.getActionMasked();

		switch (action)
		{
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_POINTER_DOWN:
			case MotionEvent.ACTION_MOVE:
			{
				pressed = true;

				break;
			}
			default:
			{
				pressed = false;

				break;
			}
		}

		if (pressed)
		{	// when is pressed calculate new positions (will trigger movement if necessary)
			updatePosition(event.getX(), event.getY());
		}
		else
		{	// no longer pressed reset movement
			moveActionCallback(0, 0);
		}

		// to get view refreshed
		invalidate();

		return true;
	}
}
