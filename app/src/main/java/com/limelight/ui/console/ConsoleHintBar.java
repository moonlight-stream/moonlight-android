package com.limelight.ui.console;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

/**
 * Bottom console prompt bar that switches between controller and touch wording
 * based on the most recent input method.
 */
public class ConsoleHintBar extends LinearLayout {
    public enum InputMode {
        CONTROLLER,
        TOUCH
    }

    private final boolean showOptions;
    private TextView selectChip;
    private TextView optionsChip;
    private TextView backChip;
    private View optionsGroup;
    private View backGroup;
    private InputMode inputMode = InputMode.CONTROLLER;
    private ViewTreeObserver.OnTouchModeChangeListener touchModeListener;

    public ConsoleHintBar(Context context) {
        this(context, null);
    }

    public ConsoleHintBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ConsoleHintBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        boolean options = true;
        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ConsoleHintBar);
            options = typedArray.getBoolean(R.styleable.ConsoleHintBar_consoleShowOptionsHint, true);
            typedArray.recycle();
        }
        showOptions = options;
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        selectChip = findViewById(R.id.hint_select_chip);
        optionsChip = findViewById(R.id.hint_options_chip);
        backChip = findViewById(R.id.hint_back_chip);
        optionsGroup = findViewById(R.id.hint_options_group);
        backGroup = findViewById(R.id.hint_back_group);
        if (optionsGroup != null) {
            optionsGroup.setVisibility(showOptions ? VISIBLE : GONE);
        }
        applyInputMode();
    }

    public void setInputMode(InputMode mode) {
        if (mode == null || mode == inputMode) {
            return;
        }
        inputMode = mode;
        applyInputMode();
    }

    public InputMode getInputMode() {
        return inputMode;
    }

    /**
     * Syncs with Android touch-mode and keeps prompts updated while attached.
     */
    public void bindToHost() {
        setInputMode(isInTouchMode() ? InputMode.TOUCH : InputMode.CONTROLLER);
        if (touchModeListener == null) {
            touchModeListener = inTouchMode ->
                    setInputMode(inTouchMode ? InputMode.TOUCH : InputMode.CONTROLLER);
        }
        getViewTreeObserver().addOnTouchModeChangeListener(touchModeListener);
    }

    public void unbindFromHost() {
        if (touchModeListener != null) {
            ViewTreeObserver observer = getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnTouchModeChangeListener(touchModeListener);
            }
        }
    }

    public void observeTouchEvent(MotionEvent event) {
        if (event == null || event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return;
        }
        if (isTouchPointer(event)) {
            setInputMode(InputMode.TOUCH);
        }
    }

    public void observeKeyEvent(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }
        if (isControllerKey(event)) {
            setInputMode(InputMode.CONTROLLER);
        }
    }

    public static void bindActivity(Activity activity, ConsoleHintBar hintBar) {
        if (hintBar != null) {
            hintBar.bindToHost();
        }
    }

    private void applyInputMode() {
        if (selectChip == null) {
            return;
        }

        boolean touch = inputMode == InputMode.TOUCH;
        selectChip.setText(touch ? R.string.console_hint_gesture_tap : R.string.console_hint_button_a);
        if (optionsChip != null) {
            optionsChip.setText(touch ? R.string.console_hint_gesture_hold : R.string.console_hint_button_y);
        }
        if (backChip != null) {
            backChip.setText(R.string.console_hint_button_b);
        }

        // Touch users already have system/gesture back; keep the bar focused on direct actions.
        if (backGroup != null) {
            backGroup.setVisibility(touch ? GONE : VISIBLE);
        }
        if (optionsGroup != null && showOptions) {
            optionsGroup.setVisibility(VISIBLE);
        }
    }

    private static boolean isTouchPointer(MotionEvent event) {
        int source = event.getSource();
        return (source & android.view.InputDevice.SOURCE_TOUCHSCREEN) != 0
                || ((source & android.view.InputDevice.SOURCE_CLASS_POINTER) != 0
                && (source & android.view.InputDevice.SOURCE_MOUSE) == 0);
    }

    private static boolean isControllerKey(KeyEvent event) {
        int source = event.getSource();
        if ((source & android.view.InputDevice.SOURCE_GAMEPAD) != 0
                || (source & android.view.InputDevice.SOURCE_JOYSTICK) != 0
                || (source & android.view.InputDevice.SOURCE_DPAD) != 0) {
            return true;
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BUTTON_MODE:
                return true;
            default:
                return false;
        }
    }
}
