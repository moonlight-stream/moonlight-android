package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.ControllerPacket;

/**
 * Created by Karim Mreisi on 30.11.2014.
 */
public class VirtualController
{
	private  static final boolean _PRINT_DEBUG_INFORMATION = false;

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

    private RelativeLayout.LayoutParams 	layoutParamsButtonStart	    = null;
    private RelativeLayout.LayoutParams 	layoutParamsButtonSelect    = null;
//    private RelativeLayout.LayoutParams 	layoutParamsButtonEscape  	= null;

	private RelativeLayout.LayoutParams 	layoutParamsDPad        	= null;

	private RelativeLayout.LayoutParams 	layoutParamsButtonA		    = null;
	private RelativeLayout.LayoutParams 	layoutParamsButtonB		    = null;
	private RelativeLayout.LayoutParams 	layoutParamsButtonX	    	= null;
	private RelativeLayout.LayoutParams 	layoutParamsButtonY	    	= null;
	private RelativeLayout.LayoutParams     layoutParamsButtonLT        = null;
	private RelativeLayout.LayoutParams     layoutParamsButtonRT        = null;
    private RelativeLayout.LayoutParams 	layoutParamsButtonLB		= null;
    private RelativeLayout.LayoutParams 	layoutParamsButtonRB		= null;

    private RelativeLayout.LayoutParams 	layoutParamsParamsStick 	= null;
	private RelativeLayout.LayoutParams 	layoutParamsParamsStick2	= null;

    private DigitalButton       buttonStart = null;
	private DigitalButton		buttonSelect		= null;
//	private DigitalButton		buttonEscape		= null;

    private DigitalPad          digitalPad          = null;

	private DigitalButton		buttonA				= null;
	private DigitalButton   	buttonB				= null;
	private DigitalButton	    buttonX				= null;
	private DigitalButton		buttonY				= null;
	private DigitalButton       buttonLT            = null;
	private DigitalButton       buttonRT            = null;
    private DigitalButton		buttonLB			= null;
    private DigitalButton		buttonRB			= null;

    private AnalogStick 		stick				= null;
	private AnalogStick			stick2				= null;

	NvConnection				connection			= null;

	private int getPercentageV(int percent)
	{
		return  (int)(((float)frame_layout.getHeight() / (float)100) * (float)percent);
	}

	private int getPercentageH(int percent)
	{
		return (int)(((float)frame_layout.getWidth() / (float)100) * (float)percent);
	}

	private void setPercentilePosition(RelativeLayout.LayoutParams parm, float pos_x, float pos_y)
	{
		parm.setMargins(
			(int)(((float)frame_layout.getWidth() / (float)100 * pos_x) - ((float)parm.width / (float)2)),
			(int)(((float)frame_layout.getHeight() / (float)100 * pos_y) - ((float)parm.height / (float)2)),
			0,
			0
		);
	}

	void refreshLayout()
	{
		relative_layout.removeAllViews();

        layoutParamsDPad            = new RelativeLayout.LayoutParams(getPercentageV(30), getPercentageV(30));

		layoutParamsParamsStick		= new RelativeLayout.LayoutParams(getPercentageV(40), getPercentageV(40));
		layoutParamsParamsStick2	= new RelativeLayout.LayoutParams(getPercentageV(40), getPercentageV(40));

		layoutParamsButtonA		= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
		layoutParamsButtonB		= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
		layoutParamsButtonX		= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
		layoutParamsButtonY		= new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
        layoutParamsButtonLT    = new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
        layoutParamsButtonRT    = new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));

        layoutParamsButtonLB    = new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));
        layoutParamsButtonRB    = new RelativeLayout.LayoutParams(getPercentageV(10), getPercentageV(10));

        layoutParamsButtonStart     = new RelativeLayout.LayoutParams(getPercentageH(12), getPercentageV(8));
        layoutParamsButtonSelect    = new RelativeLayout.LayoutParams(getPercentageH(12), getPercentageV(8));

		setPercentilePosition(layoutParamsDPad,		10,	    35);

		setPercentilePosition(layoutParamsParamsStick,		22,	    78);
		setPercentilePosition(layoutParamsParamsStick2,		78,	    78);

		setPercentilePosition(layoutParamsButtonA, 		    85, 	52);
		setPercentilePosition(layoutParamsButtonB, 		    92, 	47);
		setPercentilePosition(layoutParamsButtonX, 		    85, 	40);
		setPercentilePosition(layoutParamsButtonY, 		    92, 	35);

		setPercentilePosition(layoutParamsButtonLT, 		95, 	68);
		setPercentilePosition(layoutParamsButtonRT, 		95, 	80);

        setPercentilePosition(layoutParamsButtonLB, 		85, 	28);
        setPercentilePosition(layoutParamsButtonRB, 		92, 	23);

        setPercentilePosition(layoutParamsButtonSelect,     43,     94);
        setPercentilePosition(layoutParamsButtonStart,      57,     94);


        relative_layout.addView(digitalPad,		layoutParamsDPad);

		relative_layout.addView(stick, layoutParamsParamsStick);
		relative_layout.addView(stick2, layoutParamsParamsStick2);

		relative_layout.addView(buttonA, layoutParamsButtonA);
		relative_layout.addView(buttonB, layoutParamsButtonB);
		relative_layout.addView(buttonX, layoutParamsButtonX);
		relative_layout.addView(buttonY, layoutParamsButtonY);
        relative_layout.addView(buttonLT, layoutParamsButtonLT);
        relative_layout.addView(buttonRT, layoutParamsButtonRT);
        relative_layout.addView(buttonLB, layoutParamsButtonLB);
        relative_layout.addView(buttonRB, layoutParamsButtonRB);

        relative_layout.addView(buttonSelect, layoutParamsButtonSelect);
        relative_layout.addView(buttonStart, layoutParamsButtonStart);
	}

    private DigitalButton createDigitalButton(String text, final int key, Context context)
    {
        DigitalButton button = new DigitalButton(context);
        button.setText(text);
        button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                inputMap |= key;
                sendControllerInputPacket();
            }

            @Override
            public void onRelease() {
                inputMap &= ~key;
                sendControllerInputPacket();
            }
        });

        return  button;
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

        digitalPad = new DigitalPad(context);
        digitalPad.addDigitalPadListener(new DigitalPad.DigitalPadListener()
        {
            @Override
            public void onDirectionChange(int direction)
            {
                do
                {
                    if (direction == DigitalPad.DIGITAL_PAD_DIRECTION_NO_DIRECTION)
                    {
                        inputMap &= ~ControllerPacket.LEFT_FLAG;
                        inputMap &= ~ControllerPacket.RIGHT_FLAG;
                        inputMap &= ~ControllerPacket.UP_FLAG;
                        inputMap &= ~ControllerPacket.DOWN_FLAG;

                        break;
                    }

                    if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) > 0)
                    {
                        inputMap |= ControllerPacket.LEFT_FLAG;
                    }

                    if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) > 0)
                    {
                        inputMap |= ControllerPacket.RIGHT_FLAG;
                    }

                    if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_UP) > 0)
                    {
                        inputMap |= ControllerPacket.UP_FLAG;
                    }

                    if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) > 0)
                    {
                        inputMap |= ControllerPacket.DOWN_FLAG;
                    }
                }
                while (false);

                sendControllerInputPacket();
            }
        });

		buttonX = createDigitalButton("X", ControllerPacket.X_FLAG ,context);
		buttonY = createDigitalButton("Y", ControllerPacket.Y_FLAG ,context);
   		buttonA = createDigitalButton("A", ControllerPacket.A_FLAG ,context);
		buttonB = createDigitalButton("B", ControllerPacket.B_FLAG ,context);

		buttonLT = new DigitalButton(context);
		buttonLT.setText("LT");
        buttonLT.addDigitalButtonListener(new DigitalButton.DigitalButtonListener()
        {
            @Override
            public void onClick()
            {
                leftTrigger = (byte) (1 * 0xFF);

                sendControllerInputPacket();
            }

            @Override
            public void onRelease()
            {
                leftTrigger = (byte) (0 * 0xFF);

                sendControllerInputPacket();
            }
        });

		buttonRT = new DigitalButton(context);
		buttonRT.setText("RT");
        buttonRT.addDigitalButtonListener(new DigitalButton.DigitalButtonListener()
        {
            @Override
            public void onClick()
            {
                rightTrigger = (byte) (0xFF);

                sendControllerInputPacket();
            }

            @Override
            public void onRelease()
            {
                rightTrigger = (byte) (0);

                sendControllerInputPacket();
            }
        });

        buttonLB = createDigitalButton("LB", ControllerPacket.LB_FLAG ,context);
        buttonRB = createDigitalButton("RB", ControllerPacket.RB_FLAG ,context);

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

        buttonStart     = createDigitalButton("START", ControllerPacket.PLAY_FLAG, context);
        buttonSelect    = createDigitalButton("SELECT", ControllerPacket.SPECIAL_BUTTON_FLAG, context);

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
