package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Karim Mreisi on 30.11.2014.
 */
public class AnalogStick extends VirtualControllerElement
{
	protected static boolean _PRINT_DEBUG_INFORMATION = true;

	float radius_complete = 0;
	float radius_dead_zone = 0;
	float radius_analog_stick = 0;

	float position_pressed_x = 0;
	float position_pressed_y = 0;

	float position_stick_x = 0;
	float position_stick_y = 0;

	boolean viewPressed = false;
	boolean analogStickActive = false;

	_STICK_STATE stick_state = _STICK_STATE.NO_MOVEMENT;
	_CLICK_STATE click_state = _CLICK_STATE.SINGLE;

	List<AnalogStickListener> listeners = new ArrayList<AnalogStickListener>();
	OnTouchListener onTouchListener = null;
	private long timeoutDoubleClick = 250;
	private long timeLastClick = 0;

	public AnalogStick(Context context)
	{
		super(context);

		position_stick_x = getWidth() / 2;
		position_stick_y = getHeight() / 2;
	}

	public void addAnalogStickListener(AnalogStickListener listener)
	{
		listeners.add(listener);
	}

	public void setOnTouchListener(OnTouchListener listener)
	{
		onTouchListener = listener;
	}

	public void setColors(int normalColor, int pressedColor)
	{
		this.normalColor = normalColor;
		this.pressedColor = pressedColor;
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

		return Math.sqrt(x * x + y * y);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		radius_complete = getPercent(getCorrectWidth() / 2, 40);
		radius_dead_zone = getPercent(getCorrectWidth() / 2, 20);
		radius_analog_stick = getPercent(getCorrectWidth() / 2, 20);

		super.onSizeChanged(w, h, oldw, oldh);
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		// set transparent background
		canvas.drawColor(Color.TRANSPARENT);

		Paint paint = new Paint();
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(getPercent(getCorrectWidth() / 2, 2));

		// draw outer circle
		if (!viewPressed || click_state == _CLICK_STATE.SINGLE)
		{
			paint.setColor(normalColor);
		}
		else
		{
			paint.setColor(pressedColor);
		}

		canvas.drawRect(0,		0,
				getWidth(),	getHeight(),
				paint);

		paint.setColor(normalColor);

		// draw dead zone
		if (analogStickActive)
		{
			canvas.drawCircle(position_pressed_x, position_pressed_y, radius_dead_zone, paint);
		}
		else
		{
			canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius_dead_zone, paint);
		}

		// draw stick depending on state (no movement, moved, active(out of dead zone))
		if (analogStickActive)
		{
			switch (stick_state)
			{
				case NO_MOVEMENT:
				{
					paint.setColor(normalColor);
					canvas.drawCircle(position_stick_x, position_stick_y, radius_analog_stick, paint);

					break;
				}
				case MOVED:
				{
					paint.setColor(pressedColor);
					canvas.drawCircle(position_stick_x, position_stick_y, radius_analog_stick, paint);

					break;
				}
			}
		}
		else
		{
			paint.setColor(normalColor);
			canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius_analog_stick, paint);
		}

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
				angle = Math.PI * 3 / 2;
			}
			else if (way_x < 0)
			{
				angle = Math.PI * 1 / 2;
			}
		}
		else
		{
			if (way_x > 0)
			{
				if (way_y < 0)
				{        // first quadrant
					angle =
						3 * Math.PI / 2 + Math.atan((double) (-way_y / way_x));
				}
				else
				{        // second quadrant
					angle = Math.PI + Math.atan((double) (way_x / way_y));
				}
			}
			else
			{
				if (way_y > 0)
				{        // third quadrant
					angle = Math.PI / 2 + Math.atan((double) (way_y / -way_x));
				}
				else
				{        // fourth quadrant
					angle = 0 + Math.atan((double) (-way_x / -way_y));
				}
			}
		}

		_DBG("angle: " + angle + "  way y: " + way_y + " way x: " + way_x);

		return angle;
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

	private void clickActionCallback()
	{
		_DBG("click");

		// notify listeners
		for (AnalogStickListener listener : listeners)
		{
			listener.onClick();
		}
	}

	private void doubleClickActionCallback()
	{
		_DBG("double click");

		// notify listeners
		for (AnalogStickListener listener : listeners)
		{
			listener.onDoubleClick();
		}
	}

	private void revokeActionCallback()
	{
		_DBG("revoke");

		// notify listeners
		for (AnalogStickListener listener : listeners)
		{
			listener.onRevoke();
		}
	}

	private void updatePosition(float x, float y)
	{
		float way_x;
		float way_y;

		if (x > position_pressed_x)
		{
			way_x = x - position_pressed_x;

			if (way_x > radius_complete)
			{
				way_x = radius_complete;
			}
		}
		else
		{
			way_x = -(position_pressed_x - x);

			if (way_x < -radius_complete)
			{
				way_x = -radius_complete;
			}
		}

		if (y > position_pressed_y)
		{
			way_y = y - position_pressed_y;

			if (way_y > radius_complete)
			{
				way_y = radius_complete;
			}
		}
		else
		{
			way_y = -(position_pressed_y - y);

			if (way_y < -radius_complete)
			{
				way_y = -radius_complete;
			}
		}

		float movement_x = 0;
		float movement_y = 0;

		double movement_radius = getMovementRadius(way_x, way_y);
		//double movement_angle = getAngle(way_x, way_y);

		/*
		// chop radius if out of outer circle
		if (movement_radius > (radius_complete - radius_analog_stick))
		{
			movement_radius = radius_complete - radius_analog_stick;
		}

		float correlated_y =
			(float) (Math.sin(Math.PI / 2 - movement_angle) * (movement_radius));
		float correlated_x =
			(float) (Math.cos(Math.PI / 2 - movement_angle) * (movement_radius));

		float complete = (radius_complete - radius_analog_stick);

		movement_x = -(1 / complete) * correlated_x;
		movement_y = (1 / complete) * correlated_y;

		*/

		movement_x = (1 / radius_complete) * way_x;
		movement_y = -(1 / radius_complete) * way_y;

		position_stick_x = position_pressed_x + way_x;
		position_stick_y = position_pressed_y + way_y;

		// check if analog stick is outside of dead zone
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
		if (onTouchListener != null)
		{
			return onTouchListener.onTouch(this, event);
		}

		// get masked (not specific to a pointer) action
		int action = event.getActionMasked();
		_CLICK_STATE lastClickState = click_state;
		boolean wasActive = analogStickActive;

		switch (action)
		{
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_POINTER_DOWN:
			{
				position_pressed_x = event.getX();
				position_pressed_y = event.getY();

				analogStickActive = true;
				viewPressed = true;
				// check for double click
				if (lastClickState == _CLICK_STATE.SINGLE && timeLastClick + timeoutDoubleClick > System.currentTimeMillis())
				{
					click_state = _CLICK_STATE.DOUBLE;

					doubleClickActionCallback();
				}
				else
				{
					click_state = _CLICK_STATE.SINGLE;

					clickActionCallback();
				}

				timeLastClick = System.currentTimeMillis();

				break;
			}
			case MotionEvent.ACTION_MOVE:
			{
				break;
			}
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP:
			{
				analogStickActive = false;
				viewPressed = false;

				revokeActionCallback();

				break;
			}
		}

		// no longer pressed reset movement
		if (analogStickActive)
		{        // when is pressed calculate new positions (will trigger movement if necessary)
			updatePosition(event.getX(), event.getY());
		}
		else if (wasActive)
		{
			moveActionCallback(0, 0);
		}

		// to get view refreshed
		invalidate();

		return true;
	}

	private enum _STICK_STATE
	{
		NO_MOVEMENT,
		MOVED
	}


	private enum _CLICK_STATE
	{
		SINGLE,
		DOUBLE
	}

	public interface AnalogStickListener
	{
		void onMovement(float x, float y);

		void onClick();

		void onRevoke();

		void onDoubleClick();
	}
}
