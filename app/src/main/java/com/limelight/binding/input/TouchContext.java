package com.limelight.binding.input;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;

public class TouchContext {
	private int lastTouchX = 0;
	private int lastTouchY = 0;
	private int originalTouchX = 0;
	private int originalTouchY = 0;
	private long originalTouchTime = 0;
	
	private NvConnection conn;
	private int actionIndex;
	
	private static final int TAP_MOVEMENT_THRESHOLD = 10;
	private static final int TAP_TIME_THRESHOLD = 250;
	
	public TouchContext(NvConnection conn, int actionIndex)
	{
		this.conn = conn;
		this.actionIndex = actionIndex;
	}
	
	private boolean isTap()
	{
		int xDelta = Math.abs(lastTouchX - originalTouchX);
		int yDelta = Math.abs(lastTouchY - originalTouchY);
		long timeDelta = System.currentTimeMillis() - originalTouchTime;
		
		return xDelta <= TAP_MOVEMENT_THRESHOLD &&
				yDelta <= TAP_MOVEMENT_THRESHOLD &&
				timeDelta <= TAP_TIME_THRESHOLD;
	}
	
	private byte getMouseButtonIndex()
	{
		if (actionIndex == 1) {
			return MouseButtonPacket.BUTTON_RIGHT;
		}
		else {
			return MouseButtonPacket.BUTTON_LEFT;
		}
	}
	
	public boolean touchDownEvent(int eventX, int eventY)
	{
		originalTouchX = lastTouchX = eventX;
		originalTouchY = lastTouchY = eventY;
		originalTouchTime = System.currentTimeMillis();
		
		return true;
	}
	
	public void touchUpEvent(int eventX, int eventY)
	{
		if (isTap())
		{
			byte buttonIndex = getMouseButtonIndex();
			
			// Lower the mouse button
			conn.sendMouseButtonDown(buttonIndex);
			
			// We need to sleep a bit here because some games
			// do input detection by polling
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignored) {}
			
			// Raise the mouse button
			conn.sendMouseButtonUp(buttonIndex);
		}
	}
	
	public boolean touchMoveEvent(int eventX, int eventY)
	{	
		if (eventX != lastTouchX || eventY != lastTouchY)
		{
			// We only send moves for the primary touch point
			if (actionIndex == 0) {
				conn.sendMouseMove((short)(eventX - lastTouchX),
						(short)(eventY - lastTouchY));
			}
			
			lastTouchX = eventX;
			lastTouchY = eventY;
			
			return true;
		}
		
		return false;
	}
}
