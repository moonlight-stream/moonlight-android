package com.limelight;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.input.NvControllerPacket;
import com.limelight.utils.Dialog;
import com.limelight.utils.SpinnerDialog;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;


public class Game extends Activity implements OnGenericMotionListener, OnTouchListener, NvConnectionListener {
	private short inputMap = 0x0000;
	private byte leftTrigger = 0x00;
	private byte rightTrigger = 0x00;
	private short rightStickX = 0x0000;
	private short rightStickY = 0x0000;
	private short leftStickX = 0x0000;
	private short leftStickY = 0x0000;
	private int lastMouseX = Integer.MIN_VALUE;
	private int lastMouseY = Integer.MIN_VALUE;
	private int lastTouchX = 0;
	private int lastTouchY = 0;
	private boolean hasMoved = false;
	
	private NvConnection conn;
	private SpinnerDialog spinner;
	private boolean displayedFailureDialog = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Full-screen and don't let the display go off
		getWindow().setFlags(
				WindowManager.LayoutParams.FLAG_FULLSCREEN |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
				WindowManager.LayoutParams.FLAG_FULLSCREEN |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		// We don't want a title bar
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		// Inflate the content
		setContentView(R.layout.activity_game);

		// Listen for events on the game surface
		SurfaceView sv = (SurfaceView) findViewById(R.id.surfaceView);
		sv.setOnGenericMotionListener(this);
		sv.setOnTouchListener(this);
		SurfaceHolder sh = sv.getHolder();
		sh.setFixedSize(1280, 720);
		sh.setFormat(PixelFormat.RGBX_8888);

		// Start the spinner
		spinner = SpinnerDialog.displayDialog(this, "Establishing Connection", "Starting connection", true);
		
		// Start the connection
		conn = new NvConnection(Game.this.getIntent().getStringExtra("host"), Game.this, sv.getHolder().getSurface());
		conn.start();
	}

	public void hideSystemUi() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// Use immersive mode on 4.4+ or standard low profile on previous builds
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
					Game.this.getWindow().getDecorView().setSystemUiVisibility(
							View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
							View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
							View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
							View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
							View.SYSTEM_UI_FLAG_FULLSCREEN |
							View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
				}
				else {
					Game.this.getWindow().getDecorView().setSystemUiVisibility(
							View.SYSTEM_UI_FLAG_FULLSCREEN |
							View.SYSTEM_UI_FLAG_LOW_PROFILE);
				}
			}
		});
	}
	
	@Override
	public void onPause() {
		displayedFailureDialog = true;
		conn.stop();
		finish();
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		SpinnerDialog.closeDialogs();
		Dialog.closeDialogs();
		super.onDestroy();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_MENU:
			inputMap |= NvControllerPacket.PLAY_FLAG;
			break;
		case KeyEvent.KEYCODE_BACK:
		case KeyEvent.KEYCODE_BUTTON_SELECT:
			inputMap |= NvControllerPacket.BACK_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			inputMap |= NvControllerPacket.LEFT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			inputMap |= NvControllerPacket.RIGHT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			inputMap |= NvControllerPacket.UP_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			inputMap |= NvControllerPacket.DOWN_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_B:
			inputMap |= NvControllerPacket.B_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_A:
			inputMap |= NvControllerPacket.A_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_X:
			inputMap |= NvControllerPacket.X_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_Y:
			inputMap |= NvControllerPacket.Y_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_L1:
			inputMap |= NvControllerPacket.LB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_R1:
			inputMap |= NvControllerPacket.RB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBL:
			inputMap |= NvControllerPacket.LS_CLK_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBR:
			inputMap |= NvControllerPacket.RS_CLK_FLAG;
			break;
		default:
			return super.onKeyDown(keyCode, event);
		}
		
		// We detect back+start as the special button combo
		if ((inputMap & NvControllerPacket.BACK_FLAG) != 0 &&
			(inputMap & NvControllerPacket.PLAY_FLAG) != 0)
		{
			inputMap &= ~(NvControllerPacket.BACK_FLAG | NvControllerPacket.PLAY_FLAG);
			inputMap |= NvControllerPacket.SPECIAL_BUTTON_FLAG;
		}
		
		sendControllerInputPacket();
		return true;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_MENU:
			inputMap &= ~NvControllerPacket.PLAY_FLAG;
			break;
		case KeyEvent.KEYCODE_BACK:
		case KeyEvent.KEYCODE_BUTTON_SELECT:
			inputMap &= ~NvControllerPacket.BACK_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			inputMap &= ~NvControllerPacket.LEFT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			inputMap &= ~NvControllerPacket.RIGHT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			inputMap &= ~NvControllerPacket.UP_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			inputMap &= ~NvControllerPacket.DOWN_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_B:
			inputMap &= ~NvControllerPacket.B_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_A:
			inputMap &= ~NvControllerPacket.A_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_X:
			inputMap &= ~NvControllerPacket.X_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_Y:
			inputMap &= ~NvControllerPacket.Y_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_L1:
			inputMap &= ~NvControllerPacket.LB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_R1:
			inputMap &= ~NvControllerPacket.RB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBL:
			inputMap &= ~NvControllerPacket.LS_CLK_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBR:
			inputMap &= ~NvControllerPacket.RS_CLK_FLAG;
			break;
		default:
			return super.onKeyUp(keyCode, event);
		}
		
		// If one of the two is up, the special button comes up too
		if ((inputMap & NvControllerPacket.BACK_FLAG) == 0 ||
			(inputMap & NvControllerPacket.PLAY_FLAG) == 0)
		{
			inputMap &= ~NvControllerPacket.SPECIAL_BUTTON_FLAG;
		}
		
		sendControllerInputPacket();
		return true;
	}
	
	public void touchDownEvent(int eventX, int eventY)
	{
		lastTouchX = eventX;
		lastTouchY = eventY;
		hasMoved = false;
	}
	
	public void touchUpEvent(int eventX, int eventY)
	{
		if (!hasMoved)
		{
			// We haven't moved so send a click

			// Lower the mouse button
			conn.sendMouseButtonDown();
			
			// We need to sleep a bit here because some games
			// do input detection by polling
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {}
			
			// Raise the mouse button
			conn.sendMouseButtonUp();
		}
	}
	
	public void touchMoveEvent(int eventX, int eventY)
	{
		if (eventX != lastTouchX || eventY != lastTouchY)
		{
			hasMoved = true;
			conn.sendMouseMove((short)(eventX - lastTouchX),
					(short)(eventY - lastTouchY));
			
			lastTouchX = eventX;
			lastTouchY = eventY;
		}
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0)
		{
			// This case is for touch-based input devices
			if (event.getSource() == InputDevice.SOURCE_TOUCHSCREEN ||
				event.getSource() == InputDevice.SOURCE_STYLUS)
			{
				int eventX = (int)event.getX();
				int eventY = (int)event.getY();
				
				switch (event.getActionMasked())
				{
				case MotionEvent.ACTION_DOWN:
					touchDownEvent(eventX, eventY);
					break;
				case MotionEvent.ACTION_UP:
					touchUpEvent(eventX, eventY);
					break;
				case MotionEvent.ACTION_MOVE:
					touchMoveEvent(eventX, eventY);
					break;
				default:
					return super.onTouchEvent(event);
				}
			}
			// This case is for mice
			else if (event.getSource() == InputDevice.SOURCE_MOUSE)
			{
				switch (event.getActionMasked())
				{
				case MotionEvent.ACTION_DOWN:
					conn.sendMouseButtonDown();
					break;
				case MotionEvent.ACTION_UP:
					conn.sendMouseButtonUp();
					break;
				default:
					return super.onTouchEvent(event);
				}
			}
			else
			{
				return super.onTouchEvent(event);
			}

			return true;
		}
		
		return super.onTouchEvent(event);
	}
	
	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		InputDevice dev = event.getDevice();

		if (dev == null) {
			System.err.println("Unknown device");
			return false;
		}

		if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
			float LS_X = event.getAxisValue(MotionEvent.AXIS_X);
			float LS_Y = event.getAxisValue(MotionEvent.AXIS_Y);

			float RS_X, RS_Y, L2, R2;

			InputDevice.MotionRange leftTriggerRange = dev.getMotionRange(MotionEvent.AXIS_LTRIGGER);
			InputDevice.MotionRange rightTriggerRange = dev.getMotionRange(MotionEvent.AXIS_RTRIGGER);
			if (leftTriggerRange != null && rightTriggerRange != null)
			{
				// Ouya controller
				L2 = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
				R2 = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
				RS_X = event.getAxisValue(MotionEvent.AXIS_Z);
				RS_Y = event.getAxisValue(MotionEvent.AXIS_RZ);
			}
			else
			{
				InputDevice.MotionRange brakeRange = dev.getMotionRange(MotionEvent.AXIS_BRAKE);
				InputDevice.MotionRange gasRange = dev.getMotionRange(MotionEvent.AXIS_GAS);
				if (brakeRange != null && gasRange != null)
				{
					// Moga controller
					RS_X = event.getAxisValue(MotionEvent.AXIS_Z);
					RS_Y = event.getAxisValue(MotionEvent.AXIS_RZ);
					L2 = event.getAxisValue(MotionEvent.AXIS_BRAKE);
					R2 = event.getAxisValue(MotionEvent.AXIS_GAS);
				}
				else
				{
					// Xbox controller
					RS_X = event.getAxisValue(MotionEvent.AXIS_RX);
					RS_Y = event.getAxisValue(MotionEvent.AXIS_RY);
					L2 = (event.getAxisValue(MotionEvent.AXIS_Z) + 1) / 2;
					R2 = (event.getAxisValue(MotionEvent.AXIS_RZ) + 1) / 2;
				}
			}


			InputDevice.MotionRange hatXRange = dev.getMotionRange(MotionEvent.AXIS_HAT_X);
			InputDevice.MotionRange hatYRange = dev.getMotionRange(MotionEvent.AXIS_HAT_Y);
			if (hatXRange != null && hatYRange != null)
			{
				// Xbox controller D-pad
				float hatX, hatY;

				hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
				hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);

				inputMap &= ~(NvControllerPacket.LEFT_FLAG | NvControllerPacket.RIGHT_FLAG);
				inputMap &= ~(NvControllerPacket.UP_FLAG | NvControllerPacket.DOWN_FLAG);
				if (hatX < -0.5) {
					inputMap |= NvControllerPacket.LEFT_FLAG;
				}
				if (hatX > 0.5) {
					inputMap |= NvControllerPacket.RIGHT_FLAG;
				}
				if (hatY < -0.5) {
					inputMap |= NvControllerPacket.UP_FLAG;
				}
				if (hatY > 0.5) {
					inputMap |= NvControllerPacket.DOWN_FLAG;
				}
			}

			leftStickX = (short)Math.round(LS_X * 0x7FFF);
			leftStickY = (short)Math.round(-LS_Y * 0x7FFF);

			rightStickX = (short)Math.round(RS_X * 0x7FFF);
			rightStickY = (short)Math.round(-RS_Y * 0x7FFF);

			leftTrigger = (byte)Math.round(L2 * 0xFF);
			rightTrigger = (byte)Math.round(R2 * 0xFF);

			sendControllerInputPacket();
			return true;
		}
		else if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0)
		{	
			// Send a mouse move update (if neccessary)
			updateMousePosition((int)event.getX(), (int)event.getY());
			return true;
		}
	    
	    return super.onGenericMotionEvent(event);
	}
	
	private void updateMousePosition(int eventX, int eventY) {
		// Send a mouse move if we already have a mouse location
		// and the mouse coordinates change
		if (lastMouseX != Integer.MIN_VALUE &&
			lastMouseY != Integer.MIN_VALUE &&
			!(lastMouseX == eventX && lastMouseY == eventY))
		{
			conn.sendMouseMove((short)(eventX - lastMouseX),
					(short)(eventY - lastMouseY));
		}
		
		// Update pointer location for delta calculation next time
		lastMouseX = eventX;
		lastMouseY = eventY;
	}
	
	private void sendControllerInputPacket() {
		conn.sendControllerInput(inputMap, leftTrigger, rightTrigger,
				leftStickX, leftStickY, rightStickX, rightStickY);
	}

	@Override
	public boolean onGenericMotion(View v, MotionEvent event) {
		// Send it to the activity's motion event handler
		return onGenericMotionEvent(event);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		// Send it to the activity's touch event handler
		return onTouchEvent(event);
	}

	@Override
	public void stageStarting(Stage stage) {
		if (spinner != null) {
			spinner.setMessage("Starting "+stage.getName());
		}
	}

	@Override
	public void stageComplete(Stage stage) {
	}

	@Override
	public void stageFailed(Stage stage) {
		spinner.dismiss();
		spinner = null;

		if (!displayedFailureDialog) {
			displayedFailureDialog = true;
			Dialog.displayDialog(this, "Connection Error", "Starting "+stage.getName()+" failed", true);
			conn.stop();
		}
	}

	@Override
	public void connectionTerminated(Exception e) {
		if (!displayedFailureDialog) {
			displayedFailureDialog = true;
			e.printStackTrace();
			Dialog.displayDialog(this, "Connection Terminated", "The connection failed unexpectedly", true);
			conn.stop();
		}
	}

	@Override
	public void connectionStarted() {
		spinner.dismiss();
		spinner = null;
	}
}
