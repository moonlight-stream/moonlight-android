/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class KeyBoardLayoutController {
    private static final Set<Integer> MODIFIER_KEY_CODES = new HashSet<>();
    private static final Set<Integer> SPECIAL_KEY_CODES = new HashSet<>();
    private static final long POPUP_DURATION_MS = 75;

    private final long timerLongClickTimeout = 300;
    private final Context context;
    private final PreferenceConfiguration prefConfig;
    private FrameLayout frame_layout = null;
    private final Handler handler;
    public boolean shown = false;
    private final LinearLayout keyboardView;
    private PopupWindow keyPopup;
    private TextView keyPopupText;
    private Runnable hidePopupRunnable;

    static {
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_ALT_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_ALT_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_CTRL_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_CTRL_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_SHIFT_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_SHIFT_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_META_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_META_RIGHT);

        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_TAB);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_ENTER);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_SPACE);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_DEL);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_FORWARD_DEL);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_ESCAPE);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_CAPS_LOCK);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_INSERT);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_DPAD_UP);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_DPAD_DOWN);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_DPAD_LEFT);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_DPAD_RIGHT);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_PAGE_UP);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_PAGE_DOWN);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_MOVE_HOME);
        SPECIAL_KEY_CODES.add(KeyEvent.KEYCODE_MOVE_END);
    }

    private static final HashMap<Integer, Runnable> longClickRunnables = new HashMap<>();

    private final BitSet modifierKeyStates = new BitSet();

    public boolean isModifierKeyPressed(int keyCode) {
        return modifierKeyStates.get(keyCode);
    }

    private boolean isModifierKey(int keyCode) {
        if (prefConfig.stickyModifierKey) {
            return MODIFIER_KEY_CODES.contains(keyCode);
        }

        return false;
    }

    private boolean isSpecialKey(int keyCode) {
        return SPECIAL_KEY_CODES.contains(keyCode) || MODIFIER_KEY_CODES.contains(keyCode);
    }

    public KeyBoardLayoutController(FrameLayout layout, final Context context, PreferenceConfiguration prefConfig) {
        this.frame_layout = layout;
        this.context = context;
        this.prefConfig = prefConfig;
        this.keyboardView = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.layout_axixi_keyboard, null);
        this.handler = new Handler(Looper.getMainLooper());
        initKeyPopup();
        initKeyboard();
    }

    public Handler getHandler() {
        return handler;
    }

    private void initKeyboard() {
        @SuppressLint("ClickableViewAccessibility")
        View.OnTouchListener touchListener = (View v, MotionEvent event) -> {
            int eventAction = event.getAction();
            String tag = (String) v.getTag();
            if (TextUtils.equals("hide", tag)) {
                if (eventAction == MotionEvent.ACTION_UP || eventAction == MotionEvent.ACTION_CANCEL) {
                    hide();
                }
                return true;
            }

            int keyCode = Integer.parseInt(tag);
            int keyAction;
            boolean _isModifierKey = isModifierKey(keyCode);
            boolean _isSpecialKey = isSpecialKey(keyCode);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (_isModifierKey && isModifierKeyPressed(keyCode)) {
                        modifierKeyStates.clear(keyCode);
                        return true;
                    }

                    // Key popup
                    if (!TextUtils.equals("hide", tag) && !_isSpecialKey) {
                        String popupText;
                        KeyEvent tempEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
                        int unicodeChar = tempEvent.getUnicodeChar(0);

                        if (unicodeChar != 0) {
                            popupText = String.valueOf((char) unicodeChar);
                        } else {
                            popupText = KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "");
                        }

                        keyPopupText.setText(popupText);

                        // Force layout measurement
                        keyPopupText.measure(
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        );

                        int popupWidth = keyPopupText.getMeasuredWidth();

                        // Calculate position using the measured width
                        int[] location = new int[2];
                        v.getLocationInWindow(location);

                        // Center the popup over the key
                        int x = location[0] + (v.getWidth() - popupWidth) / 2;

                        // Show the popup above the key
                        int y = (int) (location[1] - v.getHeight() * 1.5);

                        keyPopup.update(x, y, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT);

                        if (keyPopup.isShowing()) {
                            keyPopup.update(x, y, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                        } else {
                            keyPopup.showAtLocation(v, Gravity.NO_GRAVITY, x, y);
                        }
                    }

                    keyAction = KeyEvent.ACTION_DOWN;
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (_isModifierKey && isModifierKeyPressed(keyCode)) {
                        return true;
                    }

                    // Remove any pending hide operations
                    handler.removeCallbacks(hidePopupRunnable);
                    // Schedule a new hide operation
                    handler.postDelayed(hidePopupRunnable, POPUP_DURATION_MS);

                    keyAction = KeyEvent.ACTION_UP;
                    break;
                default:
                    return false;
            }

            KeyEvent keyEvent = new KeyEvent(keyAction, keyCode);
            keyEvent.setSource(0);
            sendKeyEvent(keyEvent);

            if (_isModifierKey) {
                Runnable longClickRunnable = longClickRunnables.get(keyCode);
                if (longClickRunnable != null) {
                    getHandler().removeCallbacks(longClickRunnable);
                    if (keyAction == KeyEvent.ACTION_DOWN) {
                        getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
                    }
                }
            }

            if (keyAction == KeyEvent.ACTION_DOWN) {
                if (prefConfig.enableKeyboardVibrate) {
                    keyboardView.performHapticFeedback(
                            HapticFeedbackConstants.VIRTUAL_KEY,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING |
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    );
                }
                v.setBackgroundResource(R.drawable.bg_ax_keyboard_button_confirm);
            } else {
                if (prefConfig.enableKeyboardVibrate) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        keyboardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE);
                    } else {
                        keyboardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                }
                v.setBackgroundResource(R.drawable.bg_ax_keyboard_button);
            }
            return true;
        };
        for (int i = 0; i < keyboardView.getChildCount(); i++) {
            LinearLayout keyboardRow = (LinearLayout) keyboardView.getChildAt(i);
            for (int j = 0; j < keyboardRow.getChildCount(); j++) {
                View child = keyboardRow.getChildAt(j);
                keyboardRow.getChildAt(j).setOnTouchListener(touchListener);
                String keyTag = (String) child.getTag();
                if (keyTag.equals("hide")) {
                    continue;
                }
                int keycode = Integer.parseInt((String) child.getTag());
                if (isModifierKey(keycode)) {
                    longClickRunnables.put(keycode, () -> {
                        modifierKeyStates.set(keycode);
                        if (prefConfig.enableKeyboardVibrate) {
                            child.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                    });
                }
            }
        }
    }

    private void initKeyPopup() {
        // Create the popup window
        keyPopupText = new TextView(context);
        keyPopupText.setBackgroundResource(R.drawable.key_popup_background);
        keyPopupText.setTextColor(Color.WHITE);
        keyPopupText.setTextSize(32);
        keyPopupText.setGravity(Gravity.CENTER);
        keyPopupText.setPadding(24, 16, 24, 16);

        keyPopup = new PopupWindow(
                keyPopupText,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        hidePopupRunnable = () -> keyPopup.dismiss();
    }

    public void hide(boolean temporary) {
        if (prefConfig.enableKeyboardVibrate) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                keyboardView.performHapticFeedback(HapticFeedbackConstants.REJECT);
            } else {
                keyboardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        }
        keyboardView.setVisibility(View.GONE);
        if (!temporary) {
            shown = false;
        }
    }

    public void hide() {
        hide(false);
    }

    public void show() {
        keyboardView.setVisibility(View.VISIBLE);
        shown = true;
    }

    public void toggleVisibility() {
        if (keyboardView.getVisibility() == View.VISIBLE) {
            hide();
        } else {
            show();
        }
    }

    public void refreshLayout() {
        frame_layout.removeView(keyboardView);
        // DisplayMetrics screen = context.getResources().getDisplayMetrics();
        // (int)(screen.heightPixels/0.4)/
        int height = prefConfig.onscreenKeyboardHeight;
        int widthPreference = prefConfig.onscreenKeyboardWidth;
        int width = widthPreference == 1000 ? ViewGroup.LayoutParams.MATCH_PARENT : dip2px(context, widthPreference);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, dip2px(context, height));
        params.gravity = Gravity.BOTTOM;
        switch (prefConfig.onscreenKeyboardAlignMode) {
            case "left": {
                params.gravity |= Gravity.START;
                break;
            }
            case "right": {
                params.gravity |= Gravity.END;
                break;
            }
            case "center":
            default: {
                params.gravity |= Gravity.CENTER_HORIZONTAL;
            }
        }

        // params.leftMargin = 20 + buttonSize;
        // params.topMargin = 15;
        keyboardView.setAlpha(prefConfig.oscKeyboardOpacity / 100f);
        frame_layout.addView(keyboardView, params);
    }

    public int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public void sendKeyEvent(KeyEvent keyEvent) {
        if (Game.instance == null || !Game.instance.connected) {
            return;
        }
        // 1-Mouse 0-Buttons 2-Stick 3-DPad
        if (keyEvent.getSource() == 1) {
            Game.instance.mouseButtonEvent(keyEvent.getKeyCode(), KeyEvent.ACTION_DOWN == keyEvent.getAction());
        } else {
            Game.instance.onKey(null, keyEvent.getKeyCode(), keyEvent);
        }
    }
}
