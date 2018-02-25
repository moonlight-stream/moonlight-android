/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;

import com.limelight.nvstream.input.ControllerPacket;

public class RightAnalogStick extends AnalogStick {
    public RightAnalogStick(final VirtualController controller, final Context context) {
        super(controller, context, EID_RS);

        addAnalogStickListener(new AnalogStick.AnalogStickListener() {
            @Override
            public void onMovement(float x, float y) {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.rightStickX = (short) (x * 0x7FFE);
                inputContext.rightStickY = (short) (y * 0x7FFE);

                controller.sendControllerInputContext();
            }

            @Override
            public void onClick() {
            }

            @Override
            public void onDoubleClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= ControllerPacket.RS_CLK_FLAG;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRevoke() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~ControllerPacket.RS_CLK_FLAG;

                controller.sendControllerInputContext();
            }
        });
    }
}
