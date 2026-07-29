package com.limelight.ui.console;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.limelight.R;

public class ConsoleCardView extends FrameLayout {
    private final boolean circular;

    public ConsoleCardView(Context context) {
        this(context, null);
    }

    public ConsoleCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        boolean isCircular = false;
        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ConsoleCardView);
            isCircular = typedArray.getBoolean(R.styleable.ConsoleCardView_consoleCircular, false);
            typedArray.recycle();
        }
        circular = isCircular;

        setFocusable(true);
        setClickable(true);
        disableFrameworkHighlight();
        if (circular) {
            // Pre-rasterized PNG circles; no ShapeDrawable/outline clipping.
            setBackgroundResource(R.drawable.iris_profile_circle_img);
            setForeground(null);
            setClipChildren(false);
            setClipToPadding(false);
            setClipToOutline(false);
            setOutlineProvider(null);
            setElevation(0f);
            setStateListAnimator(null);
        } else {
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
    }

    private void disableFrameworkHighlight() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            setDefaultFocusHighlightEnabled(false);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        disableFrameworkHighlight();
    }

    @Override
    public void onDrawForeground(Canvas canvas) {
        if (circular) {
            // Skip framework foreground + default focus/hover highlight. Those draw a
            // rounded-rect halo that reads as an inner octagon on circular profiles.
            return;
        }
        super.onDrawForeground(canvas);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        disableFrameworkHighlight();
        boolean reducedMotion = LauncherUiPreferences.read(getContext()).reducedMotion;
        float scale = gainFocus && !reducedMotion ? (circular ? 1.12f : 1.07f) : 1f;
        animate().cancel();
        if (circular) {
            setBackgroundResource(gainFocus ?
                    R.drawable.iris_profile_circle_focused_img :
                    R.drawable.iris_profile_circle_img);
            animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(LauncherEffectPolicy.focusDuration(reducedMotion))
                    .start();
        } else {
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
        }
        setSelected(gainFocus);
    }
}
