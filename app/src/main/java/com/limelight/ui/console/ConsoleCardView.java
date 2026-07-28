package com.limelight.ui.console;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.limelight.R;

public class ConsoleCardView extends FrameLayout {
    public ConsoleCardView(Context context) {
        this(context, null);
    }

    public ConsoleCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setClickable(true);
        setBackgroundResource(R.drawable.iris_card_selector);
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        boolean reducedMotion = LauncherUiPreferences.read(getContext()).reducedMotion;
        float scale = gainFocus && !reducedMotion ? 1.07f : 1f;
        animate().cancel();
        animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationZ(gainFocus ? 12f : 0f)
                .setDuration(LauncherEffectPolicy.focusDuration(reducedMotion))
                .start();
        setSelected(gainFocus);
    }
}
