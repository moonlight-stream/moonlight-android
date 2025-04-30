/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;

public class LeftTrigger extends DigitalButton {
    public LeftTrigger(final VirtualController controller, final int layer, final Context context) {
        super(controller, EID_LT, layer, context);
        addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.leftTrigger = (byte) 0xFF;

                controller.sendControllerInputContext();
            }

            @Override
            public void onLongClick() {
            }

            @Override
            public void onRelease() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.leftTrigger = (byte) 0x00;

                controller.sendControllerInputContext();
            }
        });
    }
}
