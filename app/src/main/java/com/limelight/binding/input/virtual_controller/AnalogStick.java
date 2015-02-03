package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Karim Mreisi on 30.11.2014.
 */
public class AnalogStick extends VirtualControllerElement
{
	float radius_complete		= 0;
	float radius_minimum		= 0;
	float radius_dead_zone		= 0;
	float radius_analog_stick	= 0;

	float position_pressed_x 	= 0;
	float position_pressed_y 	= 0;

	float position_moved_x 		= 0;
	float position_moved_y		= 0;

	float position_stick_x 		= 0;
	float position_stick_y 		= 0;

	Paint paint			= new Paint();


	_STICK_STATE stick_state = _STICK_STATE.NO_MOVEMENT;
	_CLICK_STATE click_state = _CLICK_STATE.SINGLE;

	List<AnalogStickListener> listeners = new ArrayList<>();
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
		radius_complete 	= getPercent(getCorrectWidth() / 2, 	90);
		radius_minimum		= getPercent(getCorrectWidth() / 2, 	30);
		radius_dead_zone 	= getPercent(getCorrectWidth() / 2, 	10);
		radius_analog_stick 	= getPercent(getCorrectWidth() / 2, 	20);

		super.onSizeChanged(w, h, oldw, oldh);
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		// set transparent background
		canvas.drawColor(Color.TRANSPARENT);

		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(getPercent(getCorrectWidth() / 2, 2));

		// draw outer circle
		if (!isPressed() || click_state == _CLICK_STATE.SINGLE)
		{
			paint.setColor(normalColor);
		}
		else
		{
			paint.setColor(pressedColor);
		}

		canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius_complete, paint);

		paint.setColor(normalColor);
		// draw minimum
		canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius_minimum, paint);

		// draw stick depending on state (no movement, moved, active(out of dead zone))
		switch (stick_state)
		{
			case NO_MOVEMENT:
			{
				paint.setColor(normalColor);
				canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius_analog_stick, paint);

				break;
			}
			case MOVED_IN_DEAD_ZONE:
			{
				paint.setColor(normalColor);
				canvas.drawCircle(position_stick_x, position_stick_y, radius_analog_stick, paint);

				break;
			}
			case MOVED_ACTIVE:
			{
				paint.setColor(pressedColor);
				canvas.drawCircle(position_stick_x, position_stick_y, radius_analog_stick, paint);

				break;
			}
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

	private void updatePosition()
	{
		// get real way for each axis
		float way_center_x 	= -(getWidth() / 2 - position_moved_x);
		float way_center_y 	= -(getHeight() / 2 - position_moved_y);

		// get radius and angel of movement from center
		double movement_radius	= getMovementRadius(way_center_x, way_center_y);
		double movement_angle	= getAngle(way_center_x, way_center_y);

		// get dead zone way for each axis
		float way_pressed_x = position_pressed_x - position_moved_x;
		float way_pressed_y = position_pressed_y - position_moved_y;

		// get radius and angel from pressed position
		double movement_dead_zone_radius = getMovementRadius(way_pressed_x, way_pressed_y);

		// chop radius if out of outer circle
		if (movement_radius > (radius_complete - radius_analog_stick))
		{
			movement_radius = radius_complete - radius_analog_stick;
		}

		// calculate new positions
		float correlated_y =
			(float) (Math.sin(Math.PI / 2 - movement_angle) * (movement_radius));
		float correlated_x =
			(float) (Math.cos(Math.PI / 2 - movement_angle) * (movement_radius));

		float complete = (radius_complete - radius_analog_stick - radius_minimum);

		float movement_x;
		float movement_y;

		movement_x = -(1 / complete) * (correlated_x - (correlated_x > 0 ? radius_minimum : -radius_minimum));
		movement_y = (1 / complete) * (correlated_y - (correlated_y > 0 ? radius_minimum : -radius_minimum));

		position_stick_x = getWidth() / 2 - correlated_x;
		position_stick_y = getHeight() / 2 - correlated_y;

		// check if analog stick is outside of dead zone and minimum
		if (movement_radius > radius_minimum && movement_dead_zone_radius > radius_dead_zone)
		{
			// set active
			stick_state = _STICK_STATE.MOVED_ACTIVE;
		}

		if (stick_state == _STICK_STATE.MOVED_ACTIVE)
		{
			moveActionCallback(movement_x, movement_y);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		// get masked (not specific to a pointer) action
		int action = event.getActionMasked();
		_CLICK_STATE lastClickState = click_state;

		position_moved_x = event.getX();
		position_moved_y = event.getY();

		switch (action)
		{
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_POINTER_DOWN:
			{
				setPressed(true);
				position_pressed_x	= position_moved_x;
				position_pressed_y	= position_moved_y;
				stick_state		= _STICK_STATE.MOVED_IN_DEAD_ZONE;

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
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP:
			{
				setPressed(false);
				stick_state = _STICK_STATE.NO_MOVEMENT;

				revokeActionCallback();

				break;
			}
		}

		if (isPressed())
		{        // when is pressed calculate new positions (will trigger movement if necessary)
			updatePosition();
		}
		else
		{	// not longer pressed reset analog stick
			moveActionCallback(0, 0);
		}

		// to get view refreshed
		invalidate();

		return true;
	}

	private enum _STICK_STATE
	{
		NO_MOVEMENT,
		MOVED_IN_DEAD_ZONE,
		MOVED_ACTIVE
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
