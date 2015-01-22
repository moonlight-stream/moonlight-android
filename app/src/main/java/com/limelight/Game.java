package com.limelight;


import com.limelight.LimelightBuildProps;
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
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.Dialog;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.Vector2d;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
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
	
	private PreferenceConfiguration prefConfig;
	private Point screenSize = new Point(0, 0);
	
	private NvConnection conn;
	private SpinnerDialog spinner;
	private boolean displayedFailureDialog = false;
	private boolean connecting = false;
	private boolean connected = false;
	
	private EvdevWatcher evdevWatcher;
	private int modifierFlags = 0;
	private boolean grabbedInput = true;
	private boolean grabComboDown = false;
	
	private ConfigurableDecoderRenderer decoderRenderer;
	
	private WifiManager.WifiLock wifiLock;
	
	private int drFlags = 0;
	
	public static final String EXTRA_HOST = "Host";
	public static final String EXTRA_APP = "App";
	public static final String EXTRA_UNIQUEID = "UniqueId";
	public static final String EXTRA_STREAMING_REMOTE = "Remote";
	int repeatCount = 0;
    int count=0;
    JoyStickClass js;
    JoyStickClass rjs;
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
		spinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.conn_establishing_title),
				getResources().getString(R.string.conn_establishing_msg), true);
		
		// Read the stream preferences
		prefConfig = PreferenceConfiguration.readPreferences(this);
		switch (prefConfig.decoder) {
		case PreferenceConfiguration.FORCE_SOFTWARE_DECODER:
			drFlags |= VideoDecoderRenderer.FLAG_FORCE_SOFTWARE_DECODING;
			break;
		case PreferenceConfiguration.AUTOSELECT_DECODER:
			break;
		case PreferenceConfiguration.FORCE_HARDWARE_DECODER:
			drFlags |= VideoDecoderRenderer.FLAG_FORCE_HARDWARE_DECODING;
			break;
		}
		
		if (prefConfig.stretchVideo) {
			drFlags |= VideoDecoderRenderer.FLAG_FILL_SCREEN;
		}
		
		Display display = getWindowManager().getDefaultDisplay();
		display.getSize(screenSize);
		
		// Listen for events on the game surface
		SurfaceView sv = (SurfaceView) findViewById(R.id.surfaceView);
		sv.setOnGenericMotionListener(this);
		sv.setOnTouchListener(this);

        RelativeLayout layout_joystick = (RelativeLayout)findViewById(R.id.layout_joystick);
        RelativeLayout right_js = (RelativeLayout)findViewById(R.id.right_js);

        js = new JoyStickClass(getApplicationContext()
                , layout_joystick, R.drawable.image_button);
        js.setStickSize(150,150);
        js.setLayoutSize(500, 500);
        js.setLayoutAlpha(150);
        js.setStickAlpha(100);
        js.setOffset(90);
        js.setMinimumDistance(50);

        rjs = new JoyStickClass(getApplicationContext()
                , right_js, R.drawable.image_button);
        rjs.setStickSize(150, 150);
        rjs.setLayoutSize(500, 500);
        rjs.setLayoutAlpha(150);
        rjs.setStickAlpha(100);
        rjs.setOffset(90);
        rjs.setMinimumDistance(50);

        layout_joystick.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View arg0, MotionEvent arg1) {
                js.drawStick(arg1);
                if(arg1.getAction() == MotionEvent.ACTION_DOWN
                        || arg1.getAction() == MotionEvent.ACTION_MOVE) {
                    /*textView1.setText("X : " + String.valueOf(js.getX()));
                    textView2.setText("Y : " + String.valueOf(js.getY()));
                    textView3.setText("Angle : " + String.valueOf(js.getAngle()));
                    textView4.setText("Distance : " + String.valueOf(js.getDistance()));

                    int direction = js.get8Direction();
                    if(direction == JoyStickClass.STICK_UP) {
                        textView5.setText("Direction : Up");
                    } else if(direction == JoyStickClass.STICK_UPRIGHT) {
                        textView5.setText("Direction : Up Right");
                    } else if(direction == JoyStickClass.STICK_RIGHT) {
                        textView5.setText("Direction : Right");
                    } else if(direction == JoyStickClass.STICK_DOWNRIGHT) {
                        textView5.setText("Direction : Down Right");
                    } else if(direction == JoyStickClass.STICK_DOWN) {
                        textView5.setText("Direction : Down");
                    } else if(direction == JoyStickClass.STICK_DOWNLEFT) {
                        textView5.setText("Direction : Down Left");
                    } else if(direction == JoyStickClass.STICK_LEFT) {
                        textView5.setText("Direction : Left");
                    } else if(direction == JoyStickClass.STICK_UPLEFT) {
                        textView5.setText("Direction : Up Left");
                    } else if(direction == JoyStickClass.STICK_NONE) {
                        textView5.setText("Direction : Center");
                    }*/
                    float x = js.getX()/200.0f;
                    float y = js.getY()/200.0f;
                    handleJoyStick(0,x,y);
                    //handleJoyStick(0,js.getY(), js.getX());
                } else if(arg1.getAction() == MotionEvent.ACTION_UP) {
                   /* textView1.setText("X :");
                    textView2.setText("Y :");
                    textView3.setText("Angle :");
                    textView4.setText("Distance :");
                    textView5.setText("Direction :");*/
                    handleJoyStick(0,0,0);
                }
                return true;
            }
        });

        right_js.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View arg0, MotionEvent arg1) {
                rjs.drawStick(arg1);
                if(arg1.getAction() == MotionEvent.ACTION_DOWN
                        || arg1.getAction() == MotionEvent.ACTION_MOVE) {
                    float x = rjs.getX()/200.0f;
                    float y = rjs.getY()/200.0f;
                    handleJoyStick(1,x,y);
                } else if(arg1.getAction() == MotionEvent.ACTION_UP) {
                    handleJoyStick(1,0, 0);
                }
                return true;
            }
        });


        //START: added by hasan

        RepeatListener touchListener = new RepeatListener(100,100,new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handleButtons(v,event);

                count++;

                repeatCount++;
                if(event.getAction() == MotionEvent.ACTION_DOWN) {


                }
                else if(event.getAction() == MotionEvent.ACTION_UP){
                    repeatCount=0;
                }

                return true;
            }
        });

        ImageButton aBtn = (ImageButton) findViewById(R.id.aBtn);
        aBtn.setOnTouchListener(touchListener);
        ImageButton yBtn = (ImageButton) findViewById(R.id.yBtn);
        yBtn.setOnTouchListener(touchListener);
        ImageButton bBtn = (ImageButton) findViewById(R.id.bBtn);
        bBtn.setOnTouchListener(touchListener);

        ImageButton xBtn = (ImageButton) findViewById(R.id.xBtn);
        xBtn.setOnTouchListener(touchListener);

        ImageButton leftBtn = (ImageButton) findViewById(R.id.leftBtn);
        leftBtn.setOnTouchListener(touchListener);

        ImageButton rightBtn = (ImageButton) findViewById(R.id.rightBtn);
        rightBtn.setOnTouchListener(touchListener);

        ImageButton upBtn = (ImageButton) findViewById(R.id.upBtn);
        upBtn.setOnTouchListener(touchListener);

        ImageButton downBtn = (ImageButton) findViewById(R.id.downBtn);
        downBtn.setOnTouchListener(touchListener);

        ImageButton lbBtn = (ImageButton) findViewById(R.id.lbBtn);
        lbBtn.setOnTouchListener(touchListener);
        ImageButton ltBtn = (ImageButton) findViewById(R.id.ltBtn);
        ltBtn.setOnTouchListener(touchListener);
        ImageButton rbBtn = (ImageButton) findViewById(R.id.rbBtn);
        rbBtn.setOnTouchListener(touchListener);
        ImageButton rtBtn = (ImageButton) findViewById(R.id.rtBtn);
        rtBtn.setOnTouchListener(touchListener);

        ImageButton startBtn = (ImageButton) findViewById(R.id.startBtn);
        startBtn.setOnTouchListener(touchListener);
        ImageButton selectBtn = (ImageButton) findViewById(R.id.selectBtn);
        selectBtn.setOnTouchListener(touchListener);

        //END: added by hasan
		        
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
        boolean remote = Game.this.getIntent().getBooleanExtra(EXTRA_STREAMING_REMOTE, false);
		
		decoderRenderer = new ConfigurableDecoderRenderer();
		decoderRenderer.initializeWithFlags(drFlags);
        
		StreamConfiguration config = new StreamConfiguration.Builder()
                .setResolution(prefConfig.width, prefConfig.height)
                .setRefreshRate(prefConfig.fps)
                .setApp(app)
                .setBitrate(prefConfig.bitrate * 1000)
                .setEnableSops(prefConfig.enableSops)
                .enableAdaptiveResolution((decoderRenderer.getCapabilities() &
                        VideoDecoderRenderer.CAPABILITY_ADAPTIVE_RESOLUTION) != 0)
                .enableLocalAudioPlayback(prefConfig.playHostAudio)
                .setMaxPacketSize(remote ? 1024 : 1292)
                .setRemote(remote)
                .build();

		// Initialize the connection
		conn = new NvConnection(host, uniqueId, Game.this, config, PlatformBinding.getCryptoProvider(this));
		keybTranslator = new KeyboardTranslator(conn);
		controllerHandler = new ControllerHandler(conn, prefConfig.deadzonePercentage);
		
		SurfaceHolder sh = sv.getHolder();
		if (prefConfig.stretchVideo || !decoderRenderer.isHardwareAccelerated()) {
			// Set the surface to the size of the video
			sh.setFixedSize(prefConfig.width, prefConfig.height);
		}
		
		// Initialize touch contexts
		for (int i = 0; i < touchContextMap.length; i++) {
			touchContextMap[i] = new TouchContext(conn, i,
                    ((double)prefConfig.width / (double)screenSize.x),
                    ((double)prefConfig.height / (double)screenSize.y));
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
			displayTransientMessage(getResources().getString(R.string.conn_metered));
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
			message = getResources().getString(R.string.conn_client_latency)+" "+averageEndToEndLat+" ms";
			if (averageDecoderLat > 0) {
				message += " ("+getResources().getString(R.string.conn_client_latency_hw)+" "+averageDecoderLat+" ms)";
			}
		}
		else if (averageDecoderLat > 0) {
			message = getResources().getString(R.string.conn_hardware_latency)+" "+averageDecoderLat+" ms";
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
	private boolean handleSpecialKeys(short translatedKey, boolean down) {
		int modifierMask = 0;
		
		// Mask off the high byte
		translatedKey &= 0xff;
		
		if (translatedKey == KeyboardTranslator.VK_CONTROL) {
			modifierMask = KeyboardPacket.MODIFIER_CTRL;
		}
		else if (translatedKey == KeyboardTranslator.VK_SHIFT) {
			modifierMask = KeyboardPacket.MODIFIER_SHIFT;
		}
		else if (translatedKey == KeyboardTranslator.VK_ALT) {
			modifierMask = KeyboardPacket.MODIFIER_ALT;
		}
		
		if (down) {
			this.modifierFlags |= modifierMask;
		}
		else {
			this.modifierFlags &= ~modifierMask;
		}
		
		// Check if Ctrl+Shift+Z is pressed
		if (translatedKey == KeyboardTranslator.VK_Z &&
			(modifierFlags & (KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT)) ==
				(KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT))
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
	
	private byte getModifierState() {
		return (byte) modifierFlags;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// Pass-through virtual navigation keys
		if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
			return super.onKeyDown(keyCode, event);
		}
		
		// Try the controller handler first
		boolean handled = controllerHandler.handleButtonDown(event);
		if (!handled) {
			// Try the keyboard handler
			short translated = keybTranslator.translate(event.getKeyCode());
			if (translated == 0) {
				return super.onKeyDown(keyCode, event);
			}
			
			// Let this method take duplicate key down events
			if (handleSpecialKeys(translated, true)) {
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
		// Pass-through virtual navigation keys
		if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
			return super.onKeyUp(keyCode, event);
		}
		
		// Try the controller handler first
		boolean handled = controllerHandler.handleButtonUp(event);
		if (!handled) {
			// Try the keyboard handler
			short translated = keybTranslator.translate(event.getKeyCode());
			if (translated == 0) {
				return super.onKeyUp(keyCode, event);
			}
			
			if (handleSpecialKeys(translated, false)) {
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

                    // First process the historical events
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        for (TouchContext aTouchContextMap : touchContextMap) {
                            if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                            {
                                aTouchContextMap.touchMoveEvent(
                                        (int)event.getHistoricalX(aTouchContextMap.getActionIndex(), i),
                                        (int)event.getHistoricalY(aTouchContextMap.getActionIndex(), i));
                            }
                        }
                    }

                    // Now process the current values
                    for (TouchContext aTouchContextMap : touchContextMap) {
                        if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                        {
                            aTouchContextMap.touchMoveEvent(
                                    (int)event.getX(aTouchContextMap.getActionIndex()),
                                    (int)event.getY(aTouchContextMap.getActionIndex()));
                        }
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

                // First process the history
                for (int i = 0; i < event.getHistorySize(); i++) {
                    updateMousePosition((int)event.getHistoricalX(i), (int)event.getHistoricalY(i));
                }

                // Now process the current values
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
        return handleMotionEvent(event) || super.onTouchEvent(event);

    }



	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
        return handleMotionEvent(event) || super.onGenericMotionEvent(event);

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
			deltaX = (int)Math.round((double)deltaX * ((double)prefConfig.width / (double)screenSize.x));
			deltaY = (int)Math.round((double)deltaY * ((double)prefConfig.height / (double)screenSize.y));
			
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
			spinner.setMessage(getResources().getString(R.string.conn_starting)+" "+stage.getName());
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
			Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title),
					getResources().getString(R.string.conn_error_msg)+" "+stage.getName(), true);
		}
	}

	@Override
	public void connectionTerminated(Exception e) {
		if (!displayedFailureDialog) {
			displayedFailureDialog = true;
			e.printStackTrace();
			
			stopConnection();
			Dialog.displayDialog(this, getResources().getString(R.string.conn_terminated_title),
					getResources().getString(R.string.conn_terminated_msg), true);
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
		if (!prefConfig.disableWarnings) {
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
			if (!prefConfig.stretchVideo && decoderRenderer.isHardwareAccelerated()) {
				resizeSurfaceWithAspectRatio((SurfaceView) findViewById(R.id.surfaceView),
                        prefConfig.width, prefConfig.height);
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
			if (handleSpecialKeys(keyMap, buttonDown)) {
				return;
			}
			
			if (buttonDown) {
				keybTranslator.sendKeyDown(keyMap, getModifierState());
			}
			else {
				keybTranslator.sendKeyUp(keyMap, getModifierState());
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

    public void bOnClick(View v) {
        handleButtonDown(KeyEvent.KEYCODE_BUTTON_B);
        try {
            Thread.sleep(MINIMUM_BUTTON_DOWN_TIME_MS);
        }
        catch(Exception e)
        {

        }
        handleButtonUp(KeyEvent.KEYCODE_BUTTON_B);
    }

    public void aOnClick(View v) {
        handleButtonDown(KeyEvent.KEYCODE_BUTTON_A);
        handleButtonUp(KeyEvent.KEYCODE_BUTTON_A);
    }

    public void yOnClick(View v) {
        short inputMap = 0x0000;
        byte leftTrigger = 0x00;
        byte rightTrigger = 0x00;
        short rightStickX = 0x0000;
        short rightStickY = 0x0000;
        short leftStickX = 0x0000;
        short leftStickY = 0x0000;
        int emulatingButtonFlags = 0;
       // handleButtonPress(KeyEvent.KEYCODE_BUTTON_Y);
        inputMap |= ControllerPacket.Y_FLAG;
        conn.sendControllerInput(inputMap, leftTrigger, rightTrigger,
                leftStickX, leftStickY, rightStickX, rightStickY);
    }

    public void xOnClick(View v) {
        handleButtonDown(KeyEvent.KEYCODE_BUTTON_X);
    }

    public void startOnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_BUTTON_START);
    }

    public void selectOnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_BUTTON_SELECT);
    }

    public void l1OnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_BUTTON_L1);
    }

    public void l2OnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_BUTTON_L2);
    }

    public void r1OnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_BUTTON_R1);
    }

    public void r2OnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_BUTTON_R2);
    }

    public void thumblOnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_BUTTON_THUMBL);
    }

    public void thumbrOnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_BUTTON_THUMBR);
    }

    public void leftOnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_DPAD_LEFT);
    }

    public void rightOnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    public void upOnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_DPAD_UP);
    }

    public void downOnClick(View v) {
        handleButtonPress(KeyEvent.KEYCODE_DPAD_DOWN);
    }

    short inputMap = 0x0000;
    byte leftTrigger = 0x00;
    byte rightTrigger = 0x00;
    short rightStickX = 0x0000;
    short rightStickY = 0x0000;
    short leftStickX = 0x0000;
    short leftStickY = 0x0000;
    int emulatingButtonFlags = 0;

    private long lastLbUpTime = 0;
    private long lastRbUpTime = 0;
    private static final int MAXIMUM_BUMPER_UP_DELAY_MS = 100;

    private static final int MINIMUM_BUTTON_DOWN_TIME_MS = 25;

    private static final int EMULATING_SPECIAL = 0x1;
    private static final int EMULATING_SELECT = 0x2;

    private static final int EMULATED_SPECIAL_UP_DELAY_MS = 100;
    private static final int EMULATED_SELECT_UP_DELAY_MS = 30;


    public boolean handleButtonPress(int keyCode) {
        short inputMap = 0x0000;
        byte leftTrigger = 0x00;
        byte rightTrigger = 0x00;
        short rightStickX = 0x0000;
        short rightStickY = 0x0000;
        short leftStickX = 0x0000;
        short leftStickY = 0x0000;
        int emulatingButtonFlags = 0;

        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_MODE:
                inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                inputMap |= ControllerPacket.PLAY_FLAG;
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                inputMap |= ControllerPacket.BACK_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                inputMap |= ControllerPacket.LEFT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                inputMap |= ControllerPacket.RIGHT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                inputMap |= ControllerPacket.UP_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                inputMap |= ControllerPacket.DOWN_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                inputMap |= ControllerPacket.B_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                inputMap |= ControllerPacket.A_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                inputMap |= ControllerPacket.X_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                inputMap |= ControllerPacket.Y_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                inputMap |= ControllerPacket.LB_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                inputMap |= ControllerPacket.RB_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                inputMap |= ControllerPacket.LS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                inputMap |= ControllerPacket.RS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
                leftTrigger = (byte)0xFF;
                break;
            case KeyEvent.KEYCODE_BUTTON_R2:
                rightTrigger = (byte)0xFF;
                break;
            default:
                return false;
        }

        conn.sendControllerInput(inputMap, leftTrigger, rightTrigger,
                leftStickX, leftStickY, rightStickX, rightStickY);
        return true;
    }


    public boolean handleButtonUp(int keyCode) {

        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_MODE:
                inputMap &= ~ControllerPacket.SPECIAL_BUTTON_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                inputMap &= ~ControllerPacket.PLAY_FLAG;
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                inputMap &= ~ControllerPacket.BACK_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                inputMap &= ~ControllerPacket.LEFT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                inputMap &= ~ControllerPacket.RIGHT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                inputMap &= ~ControllerPacket.UP_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                inputMap &= ~ControllerPacket.DOWN_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                inputMap &= ~ControllerPacket.B_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                inputMap &= ~ControllerPacket.A_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                inputMap &= ~ControllerPacket.X_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                inputMap &= ~ControllerPacket.Y_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                inputMap &= ~ControllerPacket.LB_FLAG;
                lastLbUpTime = SystemClock.uptimeMillis();
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                inputMap &= ~ControllerPacket.RB_FLAG;
                lastRbUpTime = SystemClock.uptimeMillis();
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                inputMap &= ~ControllerPacket.LS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                inputMap &= ~ControllerPacket.RS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
                leftTrigger = 0;
                break;
            case KeyEvent.KEYCODE_BUTTON_R2:
                rightTrigger = 0;
                break;
            default:
                return false;
        }

        // Check if we're emulating the select button
        if ((emulatingButtonFlags & EMULATING_SELECT) != 0)
        {
            // If either start or LB is up, select comes up too
            if ((inputMap & ControllerPacket.PLAY_FLAG) == 0 ||
                    (inputMap & ControllerPacket.LB_FLAG) == 0)
            {
                inputMap &= ~ControllerPacket.BACK_FLAG;

                emulatingButtonFlags &= ~EMULATING_SELECT;

                try {
                    Thread.sleep(EMULATED_SELECT_UP_DELAY_MS);
                } catch (InterruptedException ignored) {}
            }
        }

        // Check if we're emulating the special button
        if ((emulatingButtonFlags & EMULATING_SPECIAL) != 0)
        {
            // If either start or select and RB is up, the special button comes up too
            if ((inputMap & ControllerPacket.PLAY_FLAG) == 0 ||
                    ((inputMap & ControllerPacket.BACK_FLAG) == 0 &&
                            (inputMap & ControllerPacket.RB_FLAG) == 0))
            {
                inputMap &= ~ControllerPacket.SPECIAL_BUTTON_FLAG;

                emulatingButtonFlags &= ~EMULATING_SPECIAL;

                try {
                    Thread.sleep(EMULATED_SPECIAL_UP_DELAY_MS);
                } catch (InterruptedException ignored) {}
            }
        }

        sendControllerInputPacket();
        return true;
    }

    public boolean handleButtonDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_MODE:
                inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                inputMap |= ControllerPacket.PLAY_FLAG;
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                inputMap |= ControllerPacket.BACK_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                inputMap |= ControllerPacket.LEFT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                inputMap |= ControllerPacket.RIGHT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                inputMap |= ControllerPacket.UP_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                inputMap |= ControllerPacket.DOWN_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                inputMap |= ControllerPacket.B_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                inputMap |= ControllerPacket.A_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                inputMap |= ControllerPacket.X_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                inputMap |= ControllerPacket.Y_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                inputMap |= ControllerPacket.LB_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                inputMap |= ControllerPacket.RB_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                inputMap |= ControllerPacket.LS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                inputMap |= ControllerPacket.RS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
                leftTrigger = (byte)0xFF;
                break;
            case KeyEvent.KEYCODE_BUTTON_R2:
                rightTrigger = (byte)0xFF;
                break;
            default:
                return false;
        }

        // Start+LB acts like select for controllers with one button
        if ((inputMap & ControllerPacket.PLAY_FLAG) != 0 &&
                ((inputMap & ControllerPacket.LB_FLAG) != 0 ||
                        SystemClock.uptimeMillis() - lastLbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS))
        {
            inputMap &= ~(ControllerPacket.PLAY_FLAG | ControllerPacket.LB_FLAG);
            inputMap |= ControllerPacket.BACK_FLAG;

            emulatingButtonFlags |= EMULATING_SELECT;
        }

        // We detect select+start or start+RB as the special button combo
        if (((inputMap & ControllerPacket.RB_FLAG) != 0 ||
                (SystemClock.uptimeMillis() - lastRbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS) ||
                (inputMap & ControllerPacket.BACK_FLAG) != 0) &&
                (inputMap & ControllerPacket.PLAY_FLAG) != 0)
        {
            inputMap &= ~(ControllerPacket.BACK_FLAG | ControllerPacket.PLAY_FLAG | ControllerPacket.RB_FLAG);
            inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;

            emulatingButtonFlags |= EMULATING_SPECIAL;
        }

        // Send a new input packet if this is the first instance of a button down event
        // or anytime if we're emulating a button
        if (emulatingButtonFlags != 0) {
            sendControllerInputPacket();
        }
        return true;
    }

    private void sendControllerInputPacket() {
        conn.sendControllerInput(inputMap, leftTrigger, rightTrigger,
                leftStickX, leftStickY, rightStickX, rightStickY);
    }


    boolean isDown = false;
    private void handleButtons(View v,MotionEvent event)
    {
        int keyCode = 0;

        if(v.getId() == R.id.aBtn)
            keyCode = KeyEvent.KEYCODE_BUTTON_A;
        else if(v.getId() == R.id.bBtn)
            keyCode = KeyEvent.KEYCODE_BUTTON_B;
        else if(v.getId() == R.id.yBtn)
            keyCode = KeyEvent.KEYCODE_BUTTON_Y;
        else if(v.getId() == R.id.xBtn)
            keyCode = KeyEvent.KEYCODE_BUTTON_X;
        else if(v.getId() == R.id.leftBtn)
            keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
        else if(v.getId() == R.id.rightBtn)
            keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
        else if(v.getId() == R.id.upBtn)
            keyCode = KeyEvent.KEYCODE_DPAD_UP;
        else if(v.getId() == R.id.downBtn)
            keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
        else if(v.getId() == R.id.lbBtn)
            keyCode = KeyEvent.KEYCODE_BUTTON_L1;
        else if(v.getId() == R.id.ltBtn)
            keyCode = KeyEvent.KEYCODE_BUTTON_L2;
        else if(v.getId() == R.id.rbBtn)
            keyCode = KeyEvent.KEYCODE_BUTTON_R1;
        else if(v.getId() == R.id.rtBtn)
            keyCode = KeyEvent.KEYCODE_BUTTON_R2;
        else if(v.getId() == R.id.startBtn)
            keyCode = KeyEvent.KEYCODE_BUTTON_START;
        else if(v.getId() == R.id.selectBtn)
            keyCode = KeyEvent.KEYCODE_BUTTON_SELECT;

        if(event.getAction() == MotionEvent.ACTION_DOWN) {
            if (isDown) {
                handleButtonUp(keyCode, event);
                repeatCount = 0;
                isDown = false;
            }
            handleButtonDown(keyCode, event);
            isDown = true;
        }
        else if(event.getAction() == MotionEvent.ACTION_UP)
        {
            handleButtonUp(keyCode, event);
        }
    }


    public boolean handleButtonUp(int keyCode, MotionEvent event) {

        // If the button hasn't been down long enough, sleep for a bit before sending the up event
        // This allows "instant" button presses (like OUYA's virtual menu button) to work. This
        // path should not be triggered during normal usage.
        if (SystemClock.uptimeMillis() - event.getDownTime() < MINIMUM_BUTTON_DOWN_TIME_MS)
        {
            // Since our sleep time is so short (10 ms), it shouldn't cause a problem doing this in the
            // UI thread.
            try {
                Thread.sleep(MINIMUM_BUTTON_DOWN_TIME_MS);
            } catch (InterruptedException ignored) {}
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_MODE:
                inputMap &= ~ControllerPacket.SPECIAL_BUTTON_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                inputMap &= ~ControllerPacket.PLAY_FLAG;
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                inputMap &= ~ControllerPacket.BACK_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                inputMap &= ~ControllerPacket.LEFT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                inputMap &= ~ControllerPacket.RIGHT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                inputMap &= ~ControllerPacket.UP_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                inputMap &= ~ControllerPacket.DOWN_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                inputMap &= ~ControllerPacket.B_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                inputMap &= ~ControllerPacket.A_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                inputMap &= ~ControllerPacket.X_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                inputMap &= ~ControllerPacket.Y_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                inputMap &= ~ControllerPacket.LB_FLAG;
                lastLbUpTime = SystemClock.uptimeMillis();
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                inputMap &= ~ControllerPacket.RB_FLAG;
                lastRbUpTime = SystemClock.uptimeMillis();
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                inputMap &= ~ControllerPacket.LS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                inputMap &= ~ControllerPacket.RS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
                leftTrigger = 0;
                break;
            case KeyEvent.KEYCODE_BUTTON_R2:
                rightTrigger = 0;
                break;
            default:
                return false;
        }

        // Check if we're emulating the select button
        if ((emulatingButtonFlags & EMULATING_SELECT) != 0)
        {
            // If either start or LB is up, select comes up too
            if ((inputMap & ControllerPacket.PLAY_FLAG) == 0 ||
                    (inputMap & ControllerPacket.LB_FLAG) == 0)
            {
                inputMap &= ~ControllerPacket.BACK_FLAG;

                emulatingButtonFlags &= ~EMULATING_SELECT;

                try {
                    Thread.sleep(EMULATED_SELECT_UP_DELAY_MS);
                } catch (InterruptedException ignored) {}
            }
        }

        // Check if we're emulating the special button
        if ((emulatingButtonFlags & EMULATING_SPECIAL) != 0)
        {
            // If either start or select and RB is up, the special button comes up too
            if ((inputMap & ControllerPacket.PLAY_FLAG) == 0 ||
                    ((inputMap & ControllerPacket.BACK_FLAG) == 0 &&
                            (inputMap & ControllerPacket.RB_FLAG) == 0))
            {
                inputMap &= ~ControllerPacket.SPECIAL_BUTTON_FLAG;

                emulatingButtonFlags &= ~EMULATING_SPECIAL;

                try {
                    Thread.sleep(EMULATED_SPECIAL_UP_DELAY_MS);
                } catch (InterruptedException ignored) {}
            }
        }

        sendControllerInputPacket();
        return true;
    }

    public boolean handleButtonDown(int keyCode, MotionEvent event) {

        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_MODE:
                inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                inputMap |= ControllerPacket.PLAY_FLAG;
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                inputMap |= ControllerPacket.BACK_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                inputMap |= ControllerPacket.LEFT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                inputMap |= ControllerPacket.RIGHT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                inputMap |= ControllerPacket.UP_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                inputMap |= ControllerPacket.DOWN_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                inputMap |= ControllerPacket.B_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                inputMap |= ControllerPacket.A_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                inputMap |= ControllerPacket.X_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                inputMap |= ControllerPacket.Y_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                inputMap |= ControllerPacket.LB_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                inputMap |= ControllerPacket.RB_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                inputMap |= ControllerPacket.LS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                inputMap |= ControllerPacket.RS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
                leftTrigger = (byte)0xFF;
                break;
            case KeyEvent.KEYCODE_BUTTON_R2:
                rightTrigger = (byte)0xFF;
                break;
            default:
                return false;
        }

        // Start+LB acts like select for controllers with one button
        if ((inputMap & ControllerPacket.PLAY_FLAG) != 0 &&
                ((inputMap & ControllerPacket.LB_FLAG) != 0 ||
                        SystemClock.uptimeMillis() - lastLbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS))
        {
            inputMap &= ~(ControllerPacket.PLAY_FLAG | ControllerPacket.LB_FLAG);
            inputMap |= ControllerPacket.BACK_FLAG;

            emulatingButtonFlags |= EMULATING_SELECT;
        }

        // We detect select+start or start+RB as the special button combo
        if (((inputMap & ControllerPacket.RB_FLAG) != 0 ||
                (SystemClock.uptimeMillis() - lastRbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS) ||
                (inputMap & ControllerPacket.BACK_FLAG) != 0) &&
                (inputMap & ControllerPacket.PLAY_FLAG) != 0)
        {
            inputMap &= ~(ControllerPacket.BACK_FLAG | ControllerPacket.PLAY_FLAG | ControllerPacket.RB_FLAG);
            inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;

            emulatingButtonFlags |= EMULATING_SPECIAL;
        }

        // Send a new input packet if this is the first instance of a button down event
        // or anytime if we're emulating a button
        if (repeatCount == 0 || emulatingButtonFlags != 0) {
            sendControllerInputPacket();
        }
        sendControllerInputPacket();
        return true;
    }

    public boolean handleJoyStick(int jsNo,float x, float y) {
        float lsX = 0, lsY = 0, rsX = 0, rsY = 0, rt = 0, lt = 0, hatX = 0, hatY = 0;
        if(jsNo == 0) {
            lsX = x;
            lsY = y;
        }
        else
        {
            rsX = x;
            rsY = y;
        }
        handleAxisSet(jsNo, lsX, lsY, rsX, rsY, lt, rt, hatX, hatY);

        return true;
    }

    private void handleAxisSet(int jsNo/*0 for left js and 1 for right*/, float lsX, float lsY, float rsX,
                               float rsY, float lt, float rt, float hatX, float hatY) {
        if(jsNo == 0) {
            Vector2d leftStickVector = populateCachedVector(lsX, lsY);

            //handleDeadZone(leftStickVector, mapping.leftStickDeadzoneRadius);

            leftStickX = (short) (leftStickVector.getX() * 0x7FFE);
            leftStickY = (short) (-leftStickVector.getY() * 0x7FFE);
        }

        if (jsNo == 1) {
            Vector2d rightStickVector = populateCachedVector(rsX, rsY);

            //handleDeadZone(rightStickVector, mapping.rightStickDeadzoneRadius);

            rightStickX = (short) (rightStickVector.getX() * 0x7FFE);
            rightStickY = (short) (-rightStickVector.getY() * 0x7FFE);
        }

        /*if (mapping.leftTriggerAxis != -1 && mapping.rightTriggerAxis != -1) {
            if (mapping.triggersIdleNegative) {
                lt = (lt + 1) / 2;
                rt = (rt + 1) / 2;
            }

            if (lt <= mapping.triggerDeadzone) {
                lt = 0;
            }
            if (rt <= mapping.triggerDeadzone) {
                rt = 0;
            }

            leftTrigger = (byte)(lt * 0xFF);
            rightTrigger = (byte)(rt * 0xFF);
        }*/

        /*if (mapping.hatXAxis != -1 && mapping.hatYAxis != -1) {
            inputMap &= ~(ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG);
            if (hatX < -0.5) {
                inputMap |= ControllerPacket.LEFT_FLAG;
            }
            else if (hatX > 0.5) {
                inputMap |= ControllerPacket.RIGHT_FLAG;
            }

            inputMap &= ~(ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG);
            if (hatY < -0.5) {
                inputMap |= ControllerPacket.UP_FLAG;
            }
            else if (hatY > 0.5) {
                inputMap |= ControllerPacket.DOWN_FLAG;
            }
        }*/

        sendControllerInputPacket();
    }

    private void handleDeadZone(Vector2d stickVector, float deadzoneRadius) {
        if (stickVector.getMagnitude() <= deadzoneRadius) {
            // Deadzone
            stickVector.initialize(0, 0);
        }

        // We're not normalizing here because we let the computer handle the deadzones.
        // Normalizing can make the deadzones larger than they should be after the computer also
        // evaluates the deadzone.
    }

    private Vector2d inputVector = new Vector2d();
    private Vector2d populateCachedVector(float x, float y) {
        // Reinitialize our cached Vector2d object
        inputVector.initialize(x, y);
        return inputVector;
    }

    float alpha = 0;
    public void onAlphaBtnClick(View v)
    {
        ImageButton a = (ImageButton)findViewById(R.id.aBtn);
        ImageButton b = (ImageButton)findViewById(R.id.bBtn);
        ImageButton x = (ImageButton)findViewById(R.id.xBtn);
        ImageButton y = (ImageButton)findViewById(R.id.yBtn);
        ImageButton lb = (ImageButton)findViewById(R.id.lbBtn);
        ImageButton lt = (ImageButton)findViewById(R.id.ltBtn);
        ImageButton rb = (ImageButton)findViewById(R.id.rbBtn);
        ImageButton rt = (ImageButton)findViewById(R.id.rtBtn);
        ImageButton start = (ImageButton)findViewById(R.id.startBtn);
        ImageButton select = (ImageButton)findViewById(R.id.selectBtn);

        ImageView dpad = (ImageView)findViewById(R.id.imageView);
        RelativeLayout ljs = (RelativeLayout)findViewById(R.id.layout_joystick);
        RelativeLayout rjs = (RelativeLayout)findViewById(R.id.right_js);

        if(alpha == 0)
            alpha = 0.3f;
        else if(alpha == 0.3f)
            alpha = 1;
        else
            alpha = 0;

        a.setAlpha(alpha);
        b.setAlpha(alpha);
        y.setAlpha(alpha);
        x.setAlpha(alpha);
        lb.setAlpha(alpha);
        lt.setAlpha(alpha);
        rb.setAlpha(alpha);
        rt.setAlpha(alpha);
        start.setAlpha(alpha);
        select.setAlpha(alpha);
        ljs.setAlpha(alpha);
        rjs.setAlpha(alpha);
        dpad.setAlpha(alpha);
    }

}
