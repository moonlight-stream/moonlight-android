package com.limelight.binding.input.virtual_controller;

import android.animation.LayoutTransition;
import android.app.ActionBar;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.ControllerPacket;

/**
 * Created by Karim Mreisi on 30.11.2014.
 */
public class VirtualController
{
	private  static final boolean _PRINT_DEBUG_INFORMATION = true;

	private static final void _DBG(String text)
	{
		if (_PRINT_DEBUG_INFORMATION)
		{
			System.out.println("VirtualController: " + text);
		}
	}
	private short 				inputMap 			= 0x0000;
	private byte 				leftTrigger 		= 0x00;
	private byte 				rightTrigger 		= 0x00;
	private short				rightStickX 		= 0x0000;
	private short 				rightStickY 		= 0x0000;
	private short 				leftStickX 			= 0x0000;
	private short 				leftStickY 			= 0x0000;

	private FrameLayout			frame_layout			= null;
	private RelativeLayout		relative_layout 		= null;

	private RelativeLayout.LayoutParams 	layoutParamsButtonDPadLeft	= null;
	private RelativeLayout.LayoutParams 	layoutParamsButtonDPadRight	= null;
	private RelativeLayout.LayoutParams 	layoutParamsButtonDPadUp	= null;
	private RelativeLayout.LayoutParams 	layoutParamsButtonDPadDown	= null;

	private RelativeLayout.LayoutParams 	layoutParamsButtonA		    = null;
	private RelativeLayout.LayoutParams 	layoutParamsButtonB		    = null;
	private RelativeLayout.LayoutParams 	layoutParamsButtonX	    	= null;
	private RelativeLayout.LayoutParams 	layoutParamsButtonY	    	= null;
	private RelativeLayout.LayoutParams 	layoutParamsButtonR1		= null;
	private RelativeLayout.LayoutParams 	layoutParamsButtonR2		= null;

	private RelativeLayout.LayoutParams 	layoutParamsParamsStick 	= null;
	private RelativeLayout.LayoutParams 	layoutParamsParamsStick2	= null;

	private Button				buttonStart			= null;
	private Button				buttonSelect		= null;
	private Button				buttonESC			= null;

	private Button				buttonDPadLeft		= null;
	private Button				buttonDPadRight		= null;
	private Button				buttonDPadUp		= null;
	private Button				buttonDPadDown		= null;

	private Button				buttonA				= null;
	private Button				buttonB				= null;
	private Button				buttonX				= null;
	private Button				buttonY				= null;
	private Button				buttonR1			= null;
	private Button				buttonR2			= null;

	private AnalogStick 		stick				= null;
	private AnalogStick			stick2				= null;

    private boolean             configuration       = false;

	NvConnection				connection			= null;

	private int getPercentageV(int percent)
	{
		return  (int)(((float)frame_layout.getHeight() / (float)100) * (float)percent);
	}

	private int getPercentageH(int percent)
	{
		return (int)(((float)frame_layout.getWidth() / (float)100) * (float)percent);
	}

	private void setPercentilePosition(RelativeLayout.LayoutParams parm, int pos_x, int pos_y)
	{
		parm.setMargins(
			(int)(((float)frame_layout.getWidth() / (float)100 * (float)pos_x) - ((float)parm.width / (float)2)),
			(int)(((float)frame_layout.getHeight() / (float)100 * (float)pos_y) - ((float)parm.height / (float)2)),
			0,
			0
		);
	}

	private void onButtonTouchEvent(View v, MotionEvent event, short key)
	{
		// get masked (not specific to a pointer) action
		int action = event.getActionMasked();

		switch (action)
		{
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_POINTER_DOWN:
			{
				inputMap |= key;

				sendControllerInputPacket();

				break;
			}

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP:
			{
				inputMap &= ~key;

				sendControllerInputPacket();

				break;
			}
		}
	}

	void refreshLayout()
	{
		relative_layout.removeAllViews();

		layoutParamsButtonDPadLeft	= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
		layoutParamsButtonDPadRight	= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
		layoutParamsButtonDPadUp	= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
		layoutParamsButtonDPadDown	= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));

		layoutParamsParamsStick		= new RelativeLayout.LayoutParams(getPercentageV(40), getPercentageV(40));
		layoutParamsParamsStick2	= new RelativeLayout.LayoutParams(getPercentageV(40), getPercentageV(40));

		layoutParamsButtonA		= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
		layoutParamsButtonB		= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
		layoutParamsButtonX		= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
		layoutParamsButtonY		= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
		layoutParamsButtonR1	= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
		layoutParamsButtonR2	= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));

		setPercentilePosition(layoutParamsButtonDPadLeft,	5,	    45);
		setPercentilePosition(layoutParamsButtonDPadRight,	15,	    45);
		setPercentilePosition(layoutParamsButtonDPadUp,		10,	    35);
		setPercentilePosition(layoutParamsButtonDPadDown,	10,	    55);

		setPercentilePosition(layoutParamsParamsStick,		22,	    78);
		setPercentilePosition(layoutParamsParamsStick2,		78,	    78);

		setPercentilePosition(layoutParamsButtonA, 		    85, 	52);
		setPercentilePosition(layoutParamsButtonB, 		    92, 	47);
		setPercentilePosition(layoutParamsButtonX, 		    85, 	40);
		setPercentilePosition(layoutParamsButtonY, 		    92, 	35);

		setPercentilePosition(layoutParamsButtonR1, 		95, 	68);
		setPercentilePosition(layoutParamsButtonR2, 		95, 	80);

		relative_layout.addView(buttonDPadLeft,		layoutParamsButtonDPadLeft);
		relative_layout.addView(buttonDPadRight,	layoutParamsButtonDPadRight);
		relative_layout.addView(buttonDPadUp, 		layoutParamsButtonDPadUp);
		relative_layout.addView(buttonDPadDown, 	layoutParamsButtonDPadDown);

		relative_layout.addView(stick, layoutParamsParamsStick);
		relative_layout.addView(stick2, layoutParamsParamsStick2);
		relative_layout.addView(buttonA, layoutParamsButtonA);
		relative_layout.addView(buttonB, layoutParamsButtonB);
		relative_layout.addView(buttonX, layoutParamsButtonX);
		relative_layout.addView(buttonY, layoutParamsButtonY);
		relative_layout.addView(buttonR1, layoutParamsButtonR1);
		relative_layout.addView(buttonR2, layoutParamsButtonR2);
	}

	public VirtualController(final NvConnection conn, FrameLayout layout, Context context, WindowManager window_manager)
	{
		this.connection		= conn;
		frame_layout		= layout;

		relative_layout = new RelativeLayout(context);

		relative_layout.addOnLayoutChangeListener(new View.OnLayoutChangeListener()
		{
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom)
			{
				refreshLayout();
			}
		});

		frame_layout.addView(relative_layout);

		buttonDPadLeft	= new Button(context);
		buttonDPadLeft.setText("LF");
		buttonDPadLeft.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				onButtonTouchEvent(v, event, ControllerPacket.LEFT_FLAG);

				return false;
			}
		});

		buttonDPadRight	= new Button(context);
		buttonDPadRight.setText("RI");
		buttonDPadRight.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				onButtonTouchEvent(v, event, ControllerPacket.RIGHT_FLAG);

				return false;
			}
		});

		buttonDPadUp	= new Button(context);
		buttonDPadUp.setText("UP");
		buttonDPadUp.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				onButtonTouchEvent(v, event, ControllerPacket.UP_FLAG);

				return false;
			}
		});

		buttonDPadDown	= new Button(context);
		buttonDPadDown.setText("DW");
		buttonDPadDown.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				onButtonTouchEvent(v, event, ControllerPacket.DOWN_FLAG);

				return false;
			}
		});

		buttonX = new Button(context);
		buttonX.setText("X");
		buttonX.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				onButtonTouchEvent(v, event, ControllerPacket.X_FLAG);

				return false;
			}
		});

		buttonY = new Button(context);
		buttonY.setText("Y");
		buttonY.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				onButtonTouchEvent(v, event, ControllerPacket.Y_FLAG);

				return false;
			}
		});

		buttonA = new Button(context);
		buttonA.setText("A");
		buttonA.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				onButtonTouchEvent(v, event, ControllerPacket.A_FLAG);

				return false;
			}
		});

		buttonB = new Button(context);
		buttonB.setText("B");
		buttonB.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				onButtonTouchEvent(v, event, ControllerPacket.B_FLAG);

				return false;
			}
		});

		buttonR1 = new Button(context);
		buttonR1.setText("LT");
		buttonR1.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				// get masked (not specific to a pointer) action
				int action = event.getActionMasked();

				switch (action)
				{
					case MotionEvent.ACTION_DOWN:
					case MotionEvent.ACTION_POINTER_DOWN:
					{
						leftTrigger = (byte)(1 * 0xFF);

						sendControllerInputPacket();

						break;
					}

					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_POINTER_UP:
					{
						leftTrigger = (byte)(0 * 0xFF);

						sendControllerInputPacket();

						break;
					}
				}

				return false;
			}
		});

		buttonR2 = new Button(context);
		buttonR2.setText("RT");
		buttonR2.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				// get masked (not specific to a pointer) action
				int action = event.getActionMasked();

				switch (action)
				{
					case MotionEvent.ACTION_DOWN:
					case MotionEvent.ACTION_POINTER_DOWN:
					{
						rightTrigger = (byte)(1 * 0xFF);

						sendControllerInputPacket();

						break;
					}

					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_POINTER_UP:
					{
						rightTrigger = (byte)(0 * 0xFF);

						sendControllerInputPacket();

						break;
					}
				}

				return false;
			}
		});

		stick = new AnalogStick(context);

		stick.addAnalogStickListener(new AnalogStick.AnalogStickListener()
		{
			@Override
			public void onMovement(float x, float y)
			{
				leftStickX = (short) (x * 0x7FFE);
				leftStickY = (short) (y * 0x7FFE);

				_DBG("LEFT STICK MOVEMENT X: "+ leftStickX + " Y: " + leftStickY);
				sendControllerInputPacket();
			}
		});

		stick2 = new AnalogStick(context);
		stick2.addAnalogStickListener(new AnalogStick.AnalogStickListener()
		{
			@Override
			public void onMovement(float x, float y)
			{
				rightStickX = (short) (x * 0x7FFE);
				rightStickY = (short) (y * 0x7FFE);

				_DBG("RIGHT STICK MOVEMENT X: "+ rightStickX + " Y: " + rightStickY);
				sendControllerInputPacket();
			}
		});


		refreshLayout();
	}

    public VirtualController(FrameLayout layout, Context context, WindowManager window_manager)
    {
        this.connection		= null;
        frame_layout		= layout;

        relative_layout = new RelativeLayout(context);

        relative_layout.addOnLayoutChangeListener(new View.OnLayoutChangeListener()
        {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom)
            {
                refreshLayout();
            }
        });

        frame_layout.addView(relative_layout);

        buttonDPadLeft	= new Button(context);
        buttonDPadLeft.setText("LF");

        buttonDPadRight	= new Button(context);
        buttonDPadRight.setText("RI");

        buttonDPadUp	= new Button(context);
        buttonDPadUp.setText("UP");

        buttonDPadDown	= new Button(context);
        buttonDPadDown.setText("DW");

        buttonX = new Button(context);
        buttonX.setText("X");

        buttonY = new Button(context);
        buttonY.setText("Y");

        buttonA = new Button(context);
        buttonA.setText("A");

        buttonB = new Button(context);
        buttonB.setText("B");

        buttonR1 = new Button(context);
        buttonR1.setText("LT");

        buttonR2 = new Button(context);
        buttonR2.setText("RT");

        stick = new AnalogStick(context);
        stick2 = new AnalogStick(context);

        configuration = true;


        // receive touch events
        frame_layout.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                _DBG("touch event");
                return  true;
            }
        });


        refreshLayout();
    }

	private void sendControllerInputPacket()
	{
		try {
            _DBG("INPUT_MAP + " + inputMap);
            _DBG("LEFT_TRIGGER " + leftTrigger);
            _DBG("RIGHT_TRIGGER " + rightTrigger);
            _DBG("LEFT STICK X: " + leftStickX + " Y: " + leftStickY);
            _DBG("RIGHT STICK X: " + rightStickX + " Y: " + rightStickY);
            _DBG("RIGHT STICK X: " + rightStickX + " Y: " + rightStickY);

            if (connection != null)
            {
                connection.sendControllerInput(inputMap, leftTrigger, rightTrigger,
                        leftStickX, leftStickY, rightStickX, rightStickY);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
	}
}
