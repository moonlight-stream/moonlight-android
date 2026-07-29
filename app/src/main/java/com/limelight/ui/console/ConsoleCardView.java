package com.limelight.ui.console;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.limelight.R;

public class ConsoleCardView extends FrameLayout {
    public ConsoleCardView(Context context) {
        this(context, null);
    }

    public ConsoleCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setClickable(true);
        setBackgroundResource(R.drawable.iris_glass_panel);
        setForeground(ContextCompat.getDrawable(context, R.drawable.iris_card_ring_selector));
        setClipChildren(false);
        setClipToPadding(false);
        final float cornerRadius = getResources().getDimension(R.dimen.console_card_radius);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        setClipToOutline(true);
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            setOutlineSpotShadowColor(gainFocus ? 0xA079E7FF : 0x66000000);
            setOutlineAmbientShadowColor(gainFocus ? 0x4079E7FF : 0x33000000);
        }
        setSelected(gainFocus);
    }
}
