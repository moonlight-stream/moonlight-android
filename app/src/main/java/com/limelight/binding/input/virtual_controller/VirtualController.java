/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.nvstream.NvConnection;

import java.util.ArrayList;
import java.util.List;

public class VirtualController {
    public class ControllerInputContext {
        public short inputMap = 0x0000;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;
    }

    public enum ControllerMode {
        Active,
        Configuration
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private NvConnection connection = null;
    private Context context = null;

    private FrameLayout frame_layout = null;
    private RelativeLayout relative_layout = null;

    ControllerMode currentMode = ControllerMode.Active;
    ControllerInputContext inputContext = new ControllerInputContext();

    private Button buttonConfigure = null;

    private List<VirtualControllerElement> elements = new ArrayList<>();

    public VirtualController(final NvConnection conn, FrameLayout layout, final Context context) {
        this.connection = conn;
        this.frame_layout = layout;
        this.context = context;

        relative_layout = new RelativeLayout(context);

        frame_layout.addView(relative_layout);

        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.25f);
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;

                if (currentMode == ControllerMode.Configuration) {
                    currentMode = ControllerMode.Active;
                    message = "Exiting configuration mode";
                } else {
                    currentMode = ControllerMode.Configuration;
                    message = "Entering configuration mode";
                }

                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

                relative_layout.invalidate();

                for (VirtualControllerElement element : elements) {
                    element.invalidate();
                }
            }
        });
    }

    public void removeElements() {
        for (VirtualControllerElement element : elements) {
            relative_layout.removeView(element);
        }
        elements.clear();
    }

    public void addElement(VirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        relative_layout.addView(element, layoutParams);
    }

    public List<VirtualControllerElement> getElements() {
        return elements;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            System.out.println("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        relative_layout.removeAllViews();
        removeElements();

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        int buttonSize = (int)(screen.heightPixels*0.06f);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(buttonSize, buttonSize);
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        params.leftMargin = 15;
        params.topMargin = 15;
        relative_layout.addView(buttonConfigure, params);

        VirtualControllerConfigurationLoader.createDefaultLayout(this, context);
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    public void sendControllerInputContext() {
        sendControllerInputPacket();
    }

    private void sendControllerInputPacket() {
        try {
            _DBG("INPUT_MAP + " + inputContext.inputMap);
            _DBG("LEFT_TRIGGER " + inputContext.leftTrigger);
            _DBG("RIGHT_TRIGGER " + inputContext.rightTrigger);
            _DBG("LEFT STICK X: " + inputContext.leftStickX + " Y: " + inputContext.leftStickY);
            _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);
            _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);

            if (connection != null) {
                connection.sendControllerInput(
                        inputContext.inputMap,
                        inputContext.leftTrigger,
                        inputContext.rightTrigger,
                        inputContext.leftStickX,
                        inputContext.leftStickY,
                        inputContext.rightStickX,
                        inputContext.rightStickY
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
