package com.limelight.ui.console;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.limelight.R;

public final class AmbientBackgroundView extends View {
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float phase;
    private boolean active = true;

    public AmbientBackgroundView(Context context) {
        this(context, null);
    }

    public AmbientBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void resume() {
        active = true;
        startAnimatorIfNeeded();
    }

    public void pause() {
        active = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private void startAnimatorIfNeeded() {
        if (!active || LauncherUiPreferences.read(getContext()).reducedMotion ||
                animator != null || getWindowVisibility() != VISIBLE) {
            return;
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(16000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimatorIfNeeded();
    }

    @Override
    protected void onDetachedFromWindow() {
        pause();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            startAnimatorIfNeeded();
        } else if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(getResources().getColor(R.color.iris_canvas));

        float width = getWidth();
        float height = getHeight();
        float orbit = (float) (phase * Math.PI * 2.0);
        drawGlow(canvas,
                width * (0.22f + 0.08f * (float) Math.sin(orbit)),
                height * (0.26f + 0.10f * (float) Math.cos(orbit)),
                Math.max(width, height) * 0.60f,
                Color.argb(92, 111, 123, 255));
        drawGlow(canvas,
                width * (0.82f + 0.06f * (float) Math.cos(orbit * 0.8f)),
                height * (0.70f + 0.08f * (float) Math.sin(orbit * 0.8f)),
                Math.max(width, height) * 0.52f,
                Color.argb(58, 121, 231, 255));
    }

    private void drawGlow(Canvas canvas, float x, float y, float radius, int color) {
        glowPaint.setShader(new RadialGradient(x, y, radius, color,
                Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, glowPaint);
        glowPaint.setShader(null);
    }
}
