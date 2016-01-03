/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;

import com.limelight.nvstream.input.ControllerPacket;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class VirtualControllerConfigurationLoader {
    private static final String PROFILE_PATH = "profiles";
	private static int getPercent(
		int percent,
		int total) {
		return (int) (((float) total / (float) 100) * (float) percent);
	}

	private static DigitalPad createDigitalPad(
		final VirtualController controller,
		final Context context) {

		DigitalPad digitalPad = new DigitalPad(controller, context);
		digitalPad.addDigitalPadListener(new DigitalPad.DigitalPadListener() {
			@Override
			public void onDirectionChange(int direction) {
				VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();

				if (direction == DigitalPad.DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
					inputContext.inputMap &= ~ControllerPacket.LEFT_FLAG;
					inputContext.inputMap &= ~ControllerPacket.RIGHT_FLAG;
					inputContext.inputMap &= ~ControllerPacket.UP_FLAG;
					inputContext.inputMap &= ~ControllerPacket.DOWN_FLAG;

					controller.sendControllerInputContext();
					return;
				}
				if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) > 0) {
					inputContext.inputMap |= ControllerPacket.LEFT_FLAG;
				}
				if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) > 0) {
					inputContext.inputMap |= ControllerPacket.RIGHT_FLAG;
				}
				if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_UP) > 0) {
					inputContext.inputMap |= ControllerPacket.UP_FLAG;
				}
				if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) > 0) {
					inputContext.inputMap |= ControllerPacket.DOWN_FLAG;
				}
				controller.sendControllerInputContext();
			}
		});

		return  digitalPad;
	}

	private static DigitalButton createDigitalButton(
		final int keyShort,
		final int keyLong,
		final int layer,
		final String text,
		final int icon,
		final VirtualController controller,
		final Context context) {
		DigitalButton button = new DigitalButton(controller, layer, context);
		button.setText(text);
		button.setIcon(icon);

		button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyShort;

                controller.sendControllerInputContext();
            }

            @Override
            public void onLongClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyLong;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRelease() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~keyShort;
                inputContext.inputMap &= ~keyLong;

                controller.sendControllerInputContext();
            }
        });

		return button;
	}

	private static DigitalButton createLeftTrigger(
		final int layer,
		final String text,
		final int icon,
		final VirtualController controller,
		final Context context) {
        LeftTrigger button = new LeftTrigger(controller, layer, context);
        button.setText(text);
        button.setIcon(icon);
        return button;
	}

	private static DigitalButton createRightTrigger(
		final int layer,
		final String text,
		final int icon,
		final VirtualController controller,
		final Context context) {
		RightTrigger button = new RightTrigger(controller, layer, context);
		button.setText(text);
		button.setIcon(icon);
		return button;
	}

	private static AnalogStick createLeftStick(
		final VirtualController controller,
		final Context context) {
        return new LeftAnalogStick(controller, context);
	}

	private static AnalogStick createRightStick(
		final VirtualController controller,
		final Context context) {
		return new RightAnalogStick(controller, context);
	}

	public static void createDefaultLayout(final VirtualController controller, final Context context) {

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

		controller.addElement(createDigitalPad(controller, context),
            getPercent(5, screen.widthPixels),
            getPercent(10, screen.heightPixels),
            getPercent(30, screen.widthPixels),
            getPercent(40, screen.heightPixels)
        );

		controller.addElement(createDigitalButton(
                        ControllerPacket.A_FLAG, 0, 1, "A", -1, controller, context),
            getPercent(75, screen.widthPixels),
            getPercent(40, screen.heightPixels),
            getPercent(10, screen.widthPixels),
            getPercent(10, screen.heightPixels)
		);

		controller.addElement(createDigitalButton(
                ControllerPacket.B_FLAG, 0, 1, "B", -1, controller, context),
            getPercent(85, screen.widthPixels),
            getPercent(30, screen.heightPixels),
            getPercent(10, screen.widthPixels),
            getPercent(10, screen.heightPixels)
		);

		controller.addElement(createDigitalButton(
                ControllerPacket.X_FLAG, 0, 1, "X", -1, controller, context),
            getPercent(65, screen.widthPixels),
            getPercent(30, screen.heightPixels),
            getPercent(10, screen.widthPixels),
            getPercent(10, screen.heightPixels)
		);

		controller.addElement(createDigitalButton(
                ControllerPacket.Y_FLAG, 0, 1, "Y", -1, controller, context),
            getPercent(75, screen.widthPixels),
            getPercent(20, screen.heightPixels),
            getPercent(10, screen.widthPixels),
            getPercent(10, screen.heightPixels)
		);

        controller.addElement(createLeftTrigger(
                        0, "LT", -1, controller, context),
                getPercent(65, screen.widthPixels),
                getPercent(20, screen.heightPixels),
                getPercent(10, screen.widthPixels),
                getPercent(10, screen.heightPixels)
        );

        controller.addElement(createRightTrigger(
                        0, "RT", -1, controller, context),
                getPercent(85, screen.widthPixels),
                getPercent(20, screen.heightPixels),
                getPercent(10, screen.widthPixels),
                getPercent(10, screen.heightPixels)
        );

		controller.addElement(createLeftStick(controller, context),
			getPercent(5, screen.widthPixels),
			getPercent(50, screen.heightPixels),
			getPercent(40, screen.widthPixels),
			getPercent(50, screen.heightPixels)
		);

		controller.addElement(createRightStick(controller, context),
			getPercent(55, screen.widthPixels),
			getPercent(50, screen.heightPixels),
			getPercent(40, screen.widthPixels),
			getPercent(50, screen.heightPixels)
		);

		controller.addElement(createDigitalButton(
                ControllerPacket.SPECIAL_BUTTON_FLAG, 0, 2, "SELECT", -1, controller, context),
			getPercent(40, screen.widthPixels),
			getPercent(90, screen.heightPixels),
			getPercent(10, screen.widthPixels),
			getPercent(10, screen.heightPixels)
		);

		controller.addElement(createDigitalButton(
                        ControllerPacket.PLAY_FLAG, 0, 3, "PLAY", -1, controller, context),
                getPercent(50, screen.widthPixels),
                getPercent(90, screen.heightPixels),
                getPercent(10, screen.widthPixels),
                getPercent(10, screen.heightPixels)
        );
	}

    /*
    NOT IMPLEMENTED YET,
    this should later be used to store and load a profile for the virtual controller
    public static void saveProfile(final String name,
                                   final VirtualController controller,
                                   final Context context) {

        SharedPreferences preferences = context.getSharedPreferences(PROFILE_PATH + "/" +
                name, Activity.MODE_PRIVATE);

        JSONArray elementConfigurations = new JSONArray();
        for (VirtualControllerElement element : controller.getElements()) {
            JSONObject elementConfiguration = new JSONObject();
            try {
                elementConfiguration.put("TYPE", element.getClass().getName());
                elementConfiguration.put("CONFIGURATION", element.getConfiguration());
                elementConfigurations.put(elementConfiguration);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        SharedPreferences.Editor editor= preferences.edit();
        editor.putString("ELEMENTS", elementConfigurations.toString());
    }

	public static void loadFromPreferences(final VirtualController controller) {

	}
	*/
}