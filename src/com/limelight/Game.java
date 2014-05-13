package com.limelight;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.video.ConfigurableDecoderRenderer;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.utils.Dialog;
import com.limelight.utils.SpinnerDialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Display;
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
import android.widget.Toast;


public class Game extends Activity implements SurfaceHolder.Callback, OnGenericMotionListener, OnTouchListener, NvConnectionListener {
	private int lastMouseX = Integer.MIN_VALUE;
	private int lastMouseY = Integer.MIN_VALUE;
	private int lastButtonState = 0;
	private int lastTouchX = 0;
	private int lastTouchY = 0;
	private boolean hasMoved = false;
	
	private ControllerHandler controllerHandler;
	private KeyboardTranslator keybTranslator;
	
	private int height;
	private int width;
	private Point screenSize = new Point(0, 0);
	
	private NvConnection conn;
	private SpinnerDialog spinner;
	private boolean displayedFailureDialog = false;
	private boolean connecting = false;
	private boolean connected = false;
	
	private int drFlags = 0;
	
	public static final String PREFS_FILE_NAME = "gameprefs";
	
	public static final String WIDTH_PREF_STRING = "ResH";
	public static final String HEIGHT_PREF_STRING = "ResV";
	public static final String REFRESH_RATE_PREF_STRING = "FPS";
	public static final String DECODER_PREF_STRING = "Decoder";
	public static final String BITRATE_PREF_STRING = "Bitrate";
	
	public static final int BITRATE_FLOOR_720_30 = 2;
	public static final int BITRATE_FLOOR_720_60 = 4;
	public static final int BITRATE_FLOOR_1080_30 = 4;
	public static final int BITRATE_FLOOR_1080_60 = 10;
	
	public static final int BITRATE_DEFAULT_720_30 = 5;
	public static final int BITRATE_DEFAULT_720_60 = 10;
	public static final int BITRATE_DEFAULT_1080_30 = 10;
	public static final int BITRATE_DEFAULT_1080_60 = 30;
	
	public static final int BITRATE_CEILING = 50;
	
	public static final int DEFAULT_WIDTH = 1280;
	public static final int DEFAULT_HEIGHT = 720;
	public static final int DEFAULT_REFRESH_RATE = 60;
	public static final int DEFAULT_DECODER = 0;
	public static final int DEFAULT_BITRATE = BITRATE_DEFAULT_720_60;
	
	public static final int FORCE_HARDWARE_DECODER = -1;
	public static final int AUTOSELECT_DECODER = 0;
	public static final int FORCE_SOFTWARE_DECODER = 1;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// We don't want a title bar
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		// Full-screen and don't let the display go off
		getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_FULLSCREEN |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		// If we're going to use immersive mode, we want to have
		// the entire screen
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
					View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
					View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
			
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
		}
		
		// Change volume button behavior
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		// Inflate the content
		setContentView(R.layout.activity_game);

		// Listen for events on the game surface
		SurfaceView sv = (SurfaceView) findViewById(R.id.surfaceView);
		sv.setOnGenericMotionListener(this);
		sv.setOnTouchListener(this);

		SurfaceHolder sh = sv.getHolder();

		// Start the spinner
		spinner = SpinnerDialog.displayDialog(this, "Establishing Connection", "Starting connection", true);
		
		// Read the stream preferences
		SharedPreferences prefs = getSharedPreferences(PREFS_FILE_NAME, Context.MODE_MULTI_PROCESS);
		switch (prefs.getInt(Game.DECODER_PREF_STRING, Game.DEFAULT_DECODER)) {
		case Game.FORCE_SOFTWARE_DECODER:
			drFlags |= VideoDecoderRenderer.FLAG_FORCE_SOFTWARE_DECODING;
			break;
		case Game.AUTOSELECT_DECODER:
			break;
		case Game.FORCE_HARDWARE_DECODER:
			drFlags |= VideoDecoderRenderer.FLAG_FORCE_HARDWARE_DECODING;
			break;
		}

		int refreshRate, bitrate;
		width = prefs.getInt(WIDTH_PREF_STRING, DEFAULT_WIDTH);
		height = prefs.getInt(HEIGHT_PREF_STRING, DEFAULT_HEIGHT);
		refreshRate = prefs.getInt(REFRESH_RATE_PREF_STRING, DEFAULT_REFRESH_RATE);
		bitrate = prefs.getInt(BITRATE_PREF_STRING, DEFAULT_BITRATE);
		sh.setFixedSize(width, height);
		
		Display display = getWindowManager().getDefaultDisplay();
		display.getSize(screenSize);
		        
		// Warn the user if they're on a metered connection
        checkDataConnection();
		
		// Start the connection
		conn = new NvConnection(Game.this.getIntent().getStringExtra("host"), Game.this,
				new StreamConfiguration(width, height, refreshRate, bitrate * 1000));
		keybTranslator = new KeyboardTranslator(conn);
		controllerHandler = new ControllerHandler(conn);
		
		// The connection will be started when the surface gets created
		sh.addCallback(this);
	}
	
	private void checkDataConnection()
	{
		ConnectivityManager mgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (mgr.isActiveNetworkMetered()) {
			displayTransientMessage("Warning: Your active network connection is metered!");
		}
	}

	@SuppressLint("InlinedApi")
	private Runnable hideSystemUi = new Runnable() {
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
	};

	private void hideSystemUi() {
		Handler h = getWindow().getDecorView().getHandler();
		if (h != null) {
			h.removeCallbacks(hideSystemUi);
			h.postDelayed(hideSystemUi, 1000);               
		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		
		SpinnerDialog.closeDialogs();
		Dialog.closeDialogs();
		
		displayedFailureDialog = true;
		conn.stop();
		
		finish();
	}
	
	private static byte getModifierState(KeyEvent event) {
		byte modifier = 0;
		if (event.isShiftPressed()) {
			modifier |= KeyboardPacket.MODIFIER_SHIFT;
		}
		if (event.isCtrlPressed()) {
			modifier |= KeyboardPacket.MODIFIER_CTRL;
		}
		if (event.isAltPressed()) {
			modifier |= KeyboardPacket.MODIFIER_ALT;
		}
		return modifier;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (event.getDevice() != null &&
			(event.getDevice().getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC)) {
			short translated = keybTranslator.translate(event.getKeyCode());
			if (translated == 0) {
				return super.onKeyDown(keyCode, event);
			}
			
			keybTranslator.sendKeyDown(translated,
					getModifierState(event));
		}
		else {
			if (!controllerHandler.handleButtonDown(keyCode, event)) {
				return super.onKeyDown(keyCode, event);
			}
		}

		return true;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		// Pressing a volume button drops the immersive flag so the UI shows up again and doesn't
		// go away. I'm not sure if that's a bug or a feature, but we're working around it here
		if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			Handler h = getWindow().getDecorView().getHandler();
			if (h != null) {
				h.removeCallbacks(hideSystemUi);
				h.postDelayed(hideSystemUi, 2000);               
			}
		}

		if (event.getDevice() != null &&
			(event.getDevice().getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC)) {
			short translated = keybTranslator.translate(event.getKeyCode());
			if (translated == 0) {
				return super.onKeyUp(keyCode, event);
			}

			keybTranslator.sendKeyUp(translated,
					getModifierState(event));
		}
		else {
			if (!controllerHandler.handleButtonUp(keyCode, event)) {
				return super.onKeyUp(keyCode, event);
			}
		}
		
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
			conn.sendMouseButtonDown((byte) 0x01);
			
			// We need to sleep a bit here because some games
			// do input detection by polling
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {}
			
			// Raise the mouse button
			conn.sendMouseButtonUp((byte) 0x01);
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
				int changedButtons = event.getButtonState() ^ lastButtonState;
				
				if ((changedButtons & MotionEvent.BUTTON_PRIMARY) != 0) {
					if ((event.getButtonState() & MotionEvent.BUTTON_PRIMARY) != 0) {
						conn.sendMouseButtonDown((byte) 0x01);
					}
					else {
						conn.sendMouseButtonUp((byte) 0x01);
					}
				}
				
				if ((changedButtons & MotionEvent.BUTTON_SECONDARY) != 0) {
					if ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
						conn.sendMouseButtonDown((byte) 0x03);
					}
					else {
						conn.sendMouseButtonUp((byte) 0x03);
					}
				}
				
				if ((changedButtons & MotionEvent.BUTTON_TERTIARY) != 0) {
					if ((event.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0) {
						conn.sendMouseButtonDown((byte) 0x02);
					}
					else {
						conn.sendMouseButtonUp((byte) 0x02);
					}
				}
				
				updateMousePosition((int)event.getX(), (int)event.getY());
				
				lastButtonState = event.getButtonState();
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
		if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
			if (controllerHandler.handleMotionEvent(event)) {
				return true;
			}		
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
			int deltaX = eventX - lastMouseX;
			int deltaY = eventY - lastMouseY;
			
			// Scale the deltas if the device resolution is different
			// than the stream resolution
			deltaX = (int)Math.round((double)deltaX * ((double)width / (double)screenSize.x));
			deltaY = (int)Math.round((double)deltaY * ((double)height / (double)screenSize.y));
			
			conn.sendMouseMove((short)deltaX, (short)deltaY);
		}
		
		// Update pointer location for delta calculation next time
		lastMouseX = eventX;
		lastMouseY = eventY;
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
			connecting = false;
		}
	}

	@Override
	public void connectionTerminated(Exception e) {
		if (!displayedFailureDialog) {
			displayedFailureDialog = true;
			e.printStackTrace();
			Dialog.displayDialog(this, "Connection Terminated", "The connection failed unexpectedly", true);
			conn.stop();
			connected = false;
		}
	}

	@Override
	public void connectionStarted() {
		spinner.dismiss();
		spinner = null;
		
		connecting = false;
		connected = true;
		
		hideSystemUi();
	}

	@Override
	public void displayMessage(final String message) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(Game.this, message, Toast.LENGTH_LONG).show();
			}
		});
	}

	@Override
	public void displayTransientMessage(final String message) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(Game.this, message, Toast.LENGTH_LONG).show();
			}
		});
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!connected && !connecting) {
			connecting = true;
			conn.start(PlatformBinding.getDeviceName(), holder, drFlags,
					PlatformBinding.getAudioRenderer(), new ConfigurableDecoderRenderer());
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (connected) {
			conn.stop();
			connected = false;
		}
	}
}
