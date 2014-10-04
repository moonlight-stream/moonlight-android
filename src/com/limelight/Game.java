package com.limelight;


import com.limelight.binding.PlatformBinding;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.TouchContext;
import com.limelight.binding.input.evdev.EvdevListener;
import com.limelight.binding.input.evdev.EvdevWatcher;
import com.limelight.binding.video.ConfigurableDecoderRenderer;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.root.R;
import com.limelight.utils.Dialog;
import com.limelight.utils.SpinnerDialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
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
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;


public class Game extends Activity implements SurfaceHolder.Callback,
	OnGenericMotionListener, OnTouchListener, NvConnectionListener, EvdevListener,
	OnSystemUiVisibilityChangeListener
{
	private int lastMouseX = Integer.MIN_VALUE;
	private int lastMouseY = Integer.MIN_VALUE;
	private int lastButtonState = 0;
	
	// Only 2 touches are supported
	private TouchContext[] touchContextMap = new TouchContext[2];
	
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
	
	private boolean stretchToFit;
	private boolean toastsDisabled;
	
	private EvdevWatcher evdevWatcher;
	private int modifierFlags = 0;
	private boolean grabbedInput = true;
	private boolean grabComboDown = false;
	private static final int MODIFIER_CTRL = 0x1;
	private static final int MODIFIER_SHIFT = 0x2;
	
	private ConfigurableDecoderRenderer decoderRenderer;
	
	private WifiManager.WifiLock wifiLock;
	
	private int drFlags = 0;
	
	public static final String EXTRA_HOST = "Host";
	public static final String EXTRA_APP = "App";
	public static final String EXTRA_UNIQUEID = "UniqueId";
	public static final String EXTRA_STREAMING_REMOTE = "Remote";
	
	public static final String PREFS_FILE_NAME = "gameprefs";
	
	public static final String WIDTH_PREF_STRING = "ResH";
	public static final String HEIGHT_PREF_STRING = "ResV";
	public static final String REFRESH_RATE_PREF_STRING = "FPS";
	public static final String DECODER_PREF_STRING = "Decoder";
	public static final String BITRATE_PREF_STRING = "Bitrate";
	public static final String STRETCH_PREF_STRING = "Stretch";
	public static final String SOPS_PREF_STRING = "Sops";
	public static final String DISABLE_TOASTS_PREF_STRING = "NoToasts";
	
	public static final int BITRATE_DEFAULT_720_30 = 5;
	public static final int BITRATE_DEFAULT_720_60 = 10;
	public static final int BITRATE_DEFAULT_1080_30 = 10;
	public static final int BITRATE_DEFAULT_1080_60 = 30;
		
	public static final int DEFAULT_WIDTH = 1280;
	public static final int DEFAULT_HEIGHT = 720;
	public static final int DEFAULT_REFRESH_RATE = 60;
	public static final int DEFAULT_DECODER = 0;
	public static final int DEFAULT_BITRATE = BITRATE_DEFAULT_720_60;
	public static final boolean DEFAULT_STRETCH = false;
	public static final boolean DEFAULT_SOPS = true;
	public static final boolean DEFAULT_DISABLE_TOASTS = false;
	
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
		
		// Listen for UI visibility events
		getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(this);
		
		// Change volume button behavior
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		// Inflate the content
		setContentView(R.layout.activity_game);

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
		
		stretchToFit = prefs.getBoolean(STRETCH_PREF_STRING, DEFAULT_STRETCH);
		if (stretchToFit) {
			drFlags |= VideoDecoderRenderer.FLAG_FILL_SCREEN;
		}

		int refreshRate, bitrate;
		boolean sops;
		width = prefs.getInt(WIDTH_PREF_STRING, DEFAULT_WIDTH);
		height = prefs.getInt(HEIGHT_PREF_STRING, DEFAULT_HEIGHT);
		refreshRate = prefs.getInt(REFRESH_RATE_PREF_STRING, DEFAULT_REFRESH_RATE);
		bitrate = prefs.getInt(BITRATE_PREF_STRING, DEFAULT_BITRATE);
		sops = prefs.getBoolean(SOPS_PREF_STRING, DEFAULT_SOPS);
		toastsDisabled = prefs.getBoolean(DISABLE_TOASTS_PREF_STRING, DEFAULT_DISABLE_TOASTS);
		
		Display display = getWindowManager().getDefaultDisplay();
		display.getSize(screenSize);
		
		// Listen for events on the game surface
		SurfaceView sv = (SurfaceView) findViewById(R.id.surfaceView);
		sv.setOnGenericMotionListener(this);
		sv.setOnTouchListener(this);
		        
		// Warn the user if they're on a metered connection
        checkDataConnection();
        
        // Make sure Wi-Fi is fully powered up
		WifiManager wifiMgr = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Limelight");
		wifiLock.setReferenceCounted(false);
		wifiLock.acquire();
		
		String host = Game.this.getIntent().getStringExtra(EXTRA_HOST);
		String app = Game.this.getIntent().getStringExtra(EXTRA_APP);
		String uniqueId = Game.this.getIntent().getStringExtra(EXTRA_UNIQUEID);
		
		decoderRenderer = new ConfigurableDecoderRenderer();
		decoderRenderer.initializeWithFlags(drFlags);
        
		StreamConfiguration config = 
				new StreamConfiguration(app, width, height,
						refreshRate, bitrate * 1000, sops,
						(decoderRenderer.getCapabilities() &
								VideoDecoderRenderer.CAPABILITY_ADAPTIVE_RESOLUTION) != 0);
		
		// Initialize the connection
		conn = new NvConnection(host, uniqueId, Game.this, config, PlatformBinding.getCryptoProvider(this));
		keybTranslator = new KeyboardTranslator(conn);
		controllerHandler = new ControllerHandler(conn);
		
		SurfaceHolder sh = sv.getHolder();
		if (stretchToFit || !decoderRenderer.isHardwareAccelerated()) {
			// Set the surface to the size of the video
			sh.setFixedSize(width, height);
		}
		
		// Initialize touch contexts
		for (int i = 0; i < touchContextMap.length; i++) {
			touchContextMap[i] = new TouchContext(conn, i);
		}
		
		if (LimelightBuildProps.ROOT_BUILD) {
			// Start watching for raw input
			evdevWatcher = new EvdevWatcher(this);
			evdevWatcher.start();
		}
		
		// The connection will be started when the surface gets created
		sh.addCallback(this);
	}
	
	private void resizeSurfaceWithAspectRatio(SurfaceView sv, double vidWidth, double vidHeight)
	{
		// Get the visible width of the activity
	    double visibleWidth = getWindow().getDecorView().getWidth();
	    
	    ViewGroup.LayoutParams lp = sv.getLayoutParams();
	    
	    // Calculate the new size of the SurfaceView
	    lp.width = (int) visibleWidth;
	    lp.height = (int) ((vidHeight / vidWidth) * visibleWidth);

	    // Apply the size change
	    sv.setLayoutParams(lp);
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

	private void hideSystemUi(int delay) {
		Handler h = getWindow().getDecorView().getHandler();
		if (h != null) {
			h.removeCallbacks(hideSystemUi);
			h.postDelayed(hideSystemUi, delay);               
		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		
		SpinnerDialog.closeDialogs(this);
		Dialog.closeDialogs();
		
		displayedFailureDialog = true;
		stopConnection();
		
		int averageEndToEndLat = decoderRenderer.getAverageEndToEndLatency();
		int averageDecoderLat = decoderRenderer.getAverageDecoderLatency();
		String message = null;
		if (averageEndToEndLat > 0) {
			message = "Average client-side frame latency: "+averageEndToEndLat+" ms";
			if (averageDecoderLat > 0) {
				message += " (hardware decoder latency: "+averageDecoderLat+" ms)";
			}
		}
		else if (averageDecoderLat > 0) {
			message = "Average hardware decoder latency: "+averageDecoderLat+" ms";
		}
		
		if (message != null) {
			Toast.makeText(this, message, Toast.LENGTH_LONG).show();
		}

		finish();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		wifiLock.release();
	}
	
	private Runnable toggleGrab = new Runnable() {
		@Override
		public void run() {
			
			if (evdevWatcher != null) {
				if (grabbedInput) {
					evdevWatcher.ungrabAll();
				}
				else {
					evdevWatcher.regrabAll();
				}
			}
			
			grabbedInput = !grabbedInput;
		}
	};
	
	// Returns true if the key stroke was consumed
	private boolean handleMagicKeyCombos(short translatedKey, boolean down) {
		int modifierMask = 0;
		
		// Mask off the high byte
		translatedKey &= 0xff;
		
		if (translatedKey == KeyboardTranslator.VK_CONTROL) {
			modifierMask = MODIFIER_CTRL;
		}
		else if (translatedKey == KeyboardTranslator.VK_SHIFT) {
			modifierMask = MODIFIER_SHIFT;
		}
		
		if (down) {
			this.modifierFlags |= modifierMask;
		}
		else {
			this.modifierFlags &= ~modifierMask;
		}
		
		// Check if Ctrl+Shift+Z is pressed
		if (translatedKey == KeyboardTranslator.VK_Z &&
			(modifierFlags & (MODIFIER_CTRL|MODIFIER_SHIFT)) == (MODIFIER_CTRL|MODIFIER_SHIFT))
		{
			if (down) {
				// Now that we've pressed the magic combo
				// we'll wait for one of the keys to come up
				grabComboDown = true;
			}
			else {
				// Toggle the grab if Z comes up
				Handler h = getWindow().getDecorView().getHandler();
				if (h != null) {
					h.postDelayed(toggleGrab, 250);               
				}
				
				grabComboDown = false;
			}
			
			return true;
		}
		// Toggle the grab if control or shift comes up
		else if (grabComboDown) {
			Handler h = getWindow().getDecorView().getHandler();
			if (h != null) {
				h.postDelayed(toggleGrab, 250);               
			}
			
			grabComboDown = false;
			return true;
		}
		
		// Not a special combo
		return false;
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
		InputDevice dev = event.getDevice();
		if (dev == null) {
			return super.onKeyDown(keyCode, event);
		}
		
		// Pass-through virtual navigation keys
		if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
			return super.onKeyDown(keyCode, event);
		}
		
		// Try the controller handler first
		boolean handled = controllerHandler.handleButtonDown(keyCode, event);
		if (!handled) {
			// Try the keyboard handler
			short translated = keybTranslator.translate(event.getKeyCode());
			if (translated == 0) {
				return super.onKeyDown(keyCode, event);
			}
			
			// Let this method take duplicate key down events
			if (handleMagicKeyCombos(translated, true)) {
				return true;
			}
			
			// Eat repeat down events
			if (event.getRepeatCount() > 0) {
				return true;
			}
			
			// Pass through keyboard input if we're not grabbing
			if (!grabbedInput) {
				return super.onKeyDown(keyCode, event);
			}
			
			keybTranslator.sendKeyDown(translated,
					getModifierState(event));
		}
		
		return true;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		InputDevice dev = event.getDevice();
		if (dev == null) {
			return super.onKeyUp(keyCode, event);
		}
		
		// Pass-through virtual navigation keys
		if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
			return super.onKeyUp(keyCode, event);
		}
		
		// Try the controller handler first
		boolean handled = controllerHandler.handleButtonUp(keyCode, event);
		if (!handled) {
			// Try the keyboard handler
			short translated = keybTranslator.translate(event.getKeyCode());
			if (translated == 0) {
				return super.onKeyUp(keyCode, event);
			}
			
			if (handleMagicKeyCombos(translated, false)) {
				return true;
			}
			
			// Pass through keyboard input if we're not grabbing
			if (!grabbedInput) {
				return super.onKeyUp(keyCode, event);
			}
			
			keybTranslator.sendKeyUp(translated,
					getModifierState(event));
		}
		
		return true;
	}
	
	private TouchContext getTouchContext(int actionIndex)
	{	
		if (actionIndex < touchContextMap.length) {
			return touchContextMap[actionIndex];
		}
		else {
			return null;
		}
	}

	// Returns true if the event was consumed
	private boolean handleMotionEvent(MotionEvent event) {
		// Pass through keyboard input if we're not grabbing
		if (!grabbedInput) {
			return false;
		}

		if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
			if (controllerHandler.handleMotionEvent(event)) {
				return true;
			}		
		}
		else if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0)
		{
			// This case is for touch-based input devices
			if (event.getSource() == InputDevice.SOURCE_TOUCHSCREEN ||
					event.getSource() == InputDevice.SOURCE_STYLUS)
			{
				int actionIndex = event.getActionIndex();

				int eventX = (int)event.getX(actionIndex);
				int eventY = (int)event.getY(actionIndex);

				TouchContext context = getTouchContext(actionIndex);
				if (context == null) {
					return false;
				}

				switch (event.getActionMasked())
				{
				case MotionEvent.ACTION_POINTER_DOWN:
				case MotionEvent.ACTION_DOWN:
					context.touchDownEvent(eventX, eventY);
					break;
				case MotionEvent.ACTION_POINTER_UP:
				case MotionEvent.ACTION_UP:
					context.touchUpEvent(eventX, eventY);
					if (actionIndex == 0 && event.getPointerCount() > 1) {
						// The original secondary touch now becomes primary
						context.touchDownEvent((int)event.getX(1), (int)event.getY(1));
					}
					break;
				case MotionEvent.ACTION_MOVE:
					// ACTION_MOVE is special because it always has actionIndex == 0
					// We'll call the move handlers for all indexes manually
					for (int i = 0; i < touchContextMap.length; i++) {
						touchContextMap[i].touchMoveEvent(eventX, eventY);
					}
					break;
				default:
					return false;
				}
			}
			// This case is for mice
			else if (event.getSource() == InputDevice.SOURCE_MOUSE)
			{
				int changedButtons = event.getButtonState() ^ lastButtonState;
				
				if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
					// Send the vertical scroll packet
					byte vScrollClicks = (byte) event.getAxisValue(MotionEvent.AXIS_VSCROLL);
					conn.sendMouseScroll(vScrollClicks);
				}

				if ((changedButtons & MotionEvent.BUTTON_PRIMARY) != 0) {
					if ((event.getButtonState() & MotionEvent.BUTTON_PRIMARY) != 0) {
						conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
					}
					else {
						conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
					}
				}

				if ((changedButtons & MotionEvent.BUTTON_SECONDARY) != 0) {
					if ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
						conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
					}
					else {
						conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
					}
				}

				if ((changedButtons & MotionEvent.BUTTON_TERTIARY) != 0) {
					if ((event.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0) {
						conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE);
					}
					else {
						conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE);
					}
				}

				updateMousePosition((int)event.getX(), (int)event.getY());

				lastButtonState = event.getButtonState();
			}
			else
			{
				// Unknown source
				return false;
			}

			// Handled a known source
			return true;
		}

		// Unknown class
		return false;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!handleMotionEvent(event)) {
			return super.onTouchEvent(event);
		}
		
		return true;
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (!handleMotionEvent(event)) {
		    return super.onGenericMotionEvent(event);
		}
	    
		return true;
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
		return handleMotionEvent(event);
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		return handleMotionEvent(event);
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
	
	private void stopConnection() {
		if (connecting || connected) {
			connecting = connected = false;
			conn.stop();
		}
		
		// Close the Evdev watcher to allow use of captured input devices
		if (evdevWatcher != null) {
			evdevWatcher.shutdown();
			evdevWatcher = null;
		}
	}

	@Override
	public void stageFailed(Stage stage) {
		if (spinner != null) {
			spinner.dismiss();
			spinner = null;
		}

		if (!displayedFailureDialog) {
			displayedFailureDialog = true;
			stopConnection();
			Dialog.displayDialog(this, "Connection Error", "Starting "+stage.getName()+" failed", true);
		}
	}

	@Override
	public void connectionTerminated(Exception e) {
		if (!displayedFailureDialog) {
			displayedFailureDialog = true;
			e.printStackTrace();
			
			stopConnection();
			Dialog.displayDialog(this, "Connection Terminated", "The connection failed unexpectedly", true);
		}
	}

	@Override
	public void connectionStarted() {
		if (spinner != null) {
			spinner.dismiss();
			spinner = null;
		}
		
		connecting = false;
		connected = true;
		
		hideSystemUi(1000);
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
		if (!toastsDisabled) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(Game.this, message, Toast.LENGTH_LONG).show();
				}
			});	
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!connected && !connecting) {
			connecting = true;
			
			// Resize the surface to match the aspect ratio of the video
			// This must be done after the surface is created.
			if (!stretchToFit && decoderRenderer.isHardwareAccelerated()) {
				resizeSurfaceWithAspectRatio((SurfaceView) findViewById(R.id.surfaceView), width, height);
			}
			
			conn.start(PlatformBinding.getDeviceName(), holder, drFlags,
					PlatformBinding.getAudioRenderer(), decoderRenderer);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (connected) {
			stopConnection();
		}
	}

	@Override
	public void mouseMove(int deltaX, int deltaY) {
		conn.sendMouseMove((short) deltaX, (short) deltaY);
	}

	@Override
	public void mouseButtonEvent(int buttonId, boolean down) {
		byte buttonIndex;
		
		switch (buttonId)
		{
		case EvdevListener.BUTTON_LEFT:
			buttonIndex = MouseButtonPacket.BUTTON_LEFT;
			break;
		case EvdevListener.BUTTON_MIDDLE:
			buttonIndex = MouseButtonPacket.BUTTON_MIDDLE;
			break;
		case EvdevListener.BUTTON_RIGHT:
			buttonIndex = MouseButtonPacket.BUTTON_RIGHT;
			break;
		default:
			LimeLog.warning("Unhandled button: "+buttonId);
			return;
		}
		
		if (down) {
			conn.sendMouseButtonDown(buttonIndex);
		}
		else {
			conn.sendMouseButtonUp(buttonIndex);
		}
	}

	@Override
	public void mouseScroll(byte amount) {
		conn.sendMouseScroll(amount);
	}

	@Override
	public void keyboardEvent(boolean buttonDown, short keyCode) {
		short keyMap = keybTranslator.translate(keyCode);
		if (keyMap != 0) {
			if (handleMagicKeyCombos(keyMap, buttonDown)) {
				return;
			}
			
			if (buttonDown) {
				keybTranslator.sendKeyDown(keyMap, (byte) 0);
			}
			else {
				keybTranslator.sendKeyUp(keyMap, (byte) 0);
			}
		}
	}

	@Override
	public void onSystemUiVisibilityChange(int visibility) {
		// Don't do anything if we're not connected
		if (!connected) {
			return;
		}
		
		// This flag is set for all devices
		if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
			hideSystemUi(2000);
		}
		// This flag is only set on 4.4+
		else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT &&
				 (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
			hideSystemUi(2000);
		}
		// This flag is only set before 4.4+
		else if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT &&
				 (visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == 0) {
			hideSystemUi(2000);	 
		}
	}
}
