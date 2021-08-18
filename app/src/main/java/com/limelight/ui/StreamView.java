package com.limelight.ui;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class StreamView extends SurfaceView {
    private double desiredAspectRatio;
    private InputCallbacks inputCallbacks;

    public void setDesiredAspectRatio(double aspectRatio) {
        this.desiredAspectRatio = aspectRatio;
    }

    public void setInputCallbacks(InputCallbacks callbacks) {
        this.inputCallbacks = callbacks;
    }

    public StreamView(Context context) {
        super(context);

        setFocusableInTouchMode(true);
        setFocusable(true);
        requestFocus();
    }

    public StreamView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setFocusableInTouchMode(true);
        setFocusable(true);
        requestFocus();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // If no fixed aspect ratio has been provided, simply use the default onMeasure() behavior
        if (desiredAspectRatio == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Based on code from: https://www.buzzingandroid.com/2012/11/easy-measuring-of-custom-views-with-specific-aspect-ratio/
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int measuredHeight, measuredWidth;
        if (widthSize > heightSize * desiredAspectRatio) {
            measuredHeight = heightSize;
            measuredWidth = (int)(measuredHeight * desiredAspectRatio);
        } else {
            measuredWidth = widthSize;
            measuredHeight = (int)(measuredWidth / desiredAspectRatio);
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // This callbacks allows us to override dumb IME behavior like when
        // Samsung's default keyboard consumes Shift+Space.
        if (inputCallbacks != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (inputCallbacks.handleKeyDown(event)) {
                    return true;
                }
            }
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (inputCallbacks.handleKeyUp(event)) {
                    return true;
                }
            }
        }

        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT;

        // TYPE_TEXT_VARIATION_VISIBLE_PASSWORD disables a bunch of the fancy IME
        // stuff that we don't support (suggestions, auto-correct, GIFs, etc).
        outAttrs.inputType |= InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;

        // Don't show suggestions in the IME. We want everything to go through
        // commitText() rather than setComposingText().
        outAttrs.inputType |= InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;

        // Don't take up the whole screen in landscape mode, since the user
        // needs to see at least some of the PC's screen.
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;

        return new StreamViewInputConnection(this, true);
    }

    class StreamViewInputConnection extends BaseInputConnection {
        private final KeyCharacterMap kcm;

        private StreamViewInputConnection(View targetView, boolean fullEditor) {
            super(targetView, fullEditor);
            kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (inputCallbacks != null) {
                // If all characters have a direct KeyEvent mapping, we'll send this
                // text as normal key up/down events.
                KeyEvent[] keyEvents = kcm.getEvents(text.toString().toCharArray());
                if (keyEvents != null) {
                    for (KeyEvent event : keyEvents) {
                        sendKeyEvent(event);
                    }
                }
                else {
                    // Otherwise we'll send it as UTF-8 text
                    inputCallbacks.handleTextEvent(text.toString());
                }
            }

            return super.commitText(text, newCursorPosition);
        }
    }

    public interface InputCallbacks {
        boolean handleKeyUp(KeyEvent event);
        boolean handleKeyDown(KeyEvent event);
        void handleTextEvent(String text);
    }
}
