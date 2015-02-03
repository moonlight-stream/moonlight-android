package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.view.View;

/**
 * Created by Karim on 27.01.2015.
 */
public abstract class VirtualControllerElement extends View
{
	protected static boolean _PRINT_DEBUG_INFORMATION = false;
	protected int normalColor = 0xF0888888;
	protected int pressedColor = 0xF00000FF;

	protected VirtualControllerElement(Context context)
	{
		super(context);
	}

	protected static final void _DBG(String text)
	{
		if (_PRINT_DEBUG_INFORMATION)
		{
			System.out.println(text);
		}
	}

	public void setColors(int normalColor, int pressedColor)
	{
		this.normalColor = normalColor;
		this.pressedColor = pressedColor;

		invalidate();
	}

	protected final float getPercent(float value, float percent)
	{
		return value / 100 * percent;
	}

	protected final int getCorrectWidth()
	{
		return getWidth() > getHeight() ? getHeight() : getWidth();
	}
}
