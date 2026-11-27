package com.limelight.binding.input.genshin;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Floating 8-direction virtual joystick for left-hand movement (WASD + Left Ctrl walk zone).
 * Follows the finger on touch-down in the left half of the screen and snaps back to center on release.
 */
public class GenshinJoystick {

    public GenshinProfile.ElementLayout layout;

    // Resting base center & radius (computed from safeRect)
    public float restCx;
    public float restCy;
    public float baseRadius;
    public float knobRadius;

    // Active floating center & knob coordinates
    public float activeCx;
    public float activeCy;
    public float knobX;
    public float knobY;

    // Normalized output (-1..1)
    public float normX = 0f;
    public float normY = 0f;

    public int activePointerId = -1;
    public boolean active = false;

    public GenshinJoystick(GenshinProfile.ElementLayout layout) {
        this.layout = layout;
    }

    public void updatePixelBounds(RectF safeRect, float globalSizeScale) {
        float w = safeRect.width();
        float h = safeRect.height();
        this.restCx = safeRect.left + layout.centerX * w;
        this.restCy = safeRect.top + layout.centerY * h;
        float diameter = layout.sizePct * h * globalSizeScale;
        this.baseRadius = Math.max(48f, diameter * 0.5f);
        this.knobRadius = this.baseRadius * 0.38f;
        if (!active) {
            resetToRest();
        }
    }

    public void resetToRest() {
        active = false;
        activePointerId = -1;
        activeCx = restCx;
        activeCy = restCy;
        knobX = restCx;
        knobY = restCy;
        normX = 0f;
        normY = 0f;
    }

    public boolean hitTestRest(float x, float y) {
        float dx = x - restCx;
        float dy = y - restCy;
        return (dx * dx + dy * dy) <= (baseRadius * baseRadius);
    }

    /**
     * Activates the floating joystick at the given touch-down coordinates,
     * clamped inside the left portion of safeRect so the ring stays on-screen.
     */
    public void onPointerDown(int pointerId, float x, float y, RectF safeRect) {
        activePointerId = pointerId;
        active = true;

        float minX = safeRect.left + baseRadius * 0.65f;
        float maxX = safeRect.left + safeRect.width() * 0.48f;
        float minY = safeRect.top + baseRadius * 0.65f;
        float maxY = safeRect.bottom - baseRadius * 0.65f;

        activeCx = Math.max(minX, Math.min(maxX, x));
        activeCy = Math.max(minY, Math.min(maxY, y));
        knobX = activeCx;
        knobY = activeCy;
        normX = 0f;
        normY = 0f;
    }

    public void onPointerMove(float x, float y) {
        if (!active) {
            return;
        }
        float dx = x - activeCx;
        float dy = y - activeCy;
        float dist = (float) Math.hypot(dx, dy);
        float maxTravel = baseRadius - knobRadius * 0.35f;

        if (dist > maxTravel && dist > 0f) {
            float scale = maxTravel / dist;
            dx *= scale;
            dy *= scale;
            dist = maxTravel;
        }

        knobX = activeCx + dx;
        knobY = activeCy + dy;
        normX = maxTravel > 0f ? (dx / maxTravel) : 0f;
        normY = maxTravel > 0f ? (dy / maxTravel) : 0f;
    }

    public void draw(Canvas canvas, Paint fillPaint, Paint strokePaint, Paint textPaint,
                     int globalOpacity, boolean editMode, boolean selected) {
        int effOpacity = (layout.opacity >= 0) ? layout.opacity : globalOpacity;
        int alphaBg = Math.max(20, Math.min(180, Math.round(effOpacity * 1.2f)));
        int alphaFg = Math.max(55, Math.min(255, Math.round(effOpacity * 2.3f)));

        float cx = editMode ? restCx : activeCx;
        float cy = editMode ? restCy : activeCy;
        float kx = editMode ? restCx : knobX;
        float ky = editMode ? restCy : knobY;

        // Outer ring background
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.argb(alphaBg, 18, 22, 32));
        canvas.drawCircle(cx, cy, baseRadius, fillPaint);

        // Outer ring border
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(2.5f, baseRadius * 0.04f));
        if (editMode && selected) {
            strokePaint.setColor(Color.argb(255, 80, 255, 120));
        } else if (editMode) {
            strokePaint.setColor(Color.argb(220, 255, 190, 60));
        } else {
            strokePaint.setColor(Color.argb(alphaFg, 210, 220, 240));
        }
        canvas.drawCircle(cx, cy, baseRadius, strokePaint);

        // 50% walk/run threshold inner ring
        strokePaint.setStrokeWidth(Math.max(1.5f, baseRadius * 0.02f));
        strokePaint.setColor(Color.argb(alphaFg / 2, 180, 200, 230));
        canvas.drawCircle(cx, cy, baseRadius * GenshinInputHandler.DEFAULT_WALK_THRESHOLD, strokePaint);

        // Direction indicator line & active highlight when deflected
        float mag = (float) Math.hypot(normX, normY);
        if (active && !editMode && mag >= GenshinInputHandler.DEFAULT_DEADZONE) {
            strokePaint.setStrokeWidth(Math.max(3f, baseRadius * 0.055f));
            boolean isWalk = mag < GenshinInputHandler.DEFAULT_WALK_THRESHOLD;
            strokePaint.setColor(isWalk
                    ? Color.argb(230, 120, 215, 255)
                    : Color.argb(245, 255, 210, 100));
            canvas.drawLine(cx, cy, kx, ky, strokePaint);
        }

        // Cardinal PC key hints (W / A / S / D)
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(false);
        float labelSize = Math.max(11f, baseRadius * 0.18f);
        textPaint.setTextSize(labelSize);
        textPaint.setColor(Color.argb(Math.max(40, alphaFg * 3 / 4), 255, 225, 140));
        float offset = baseRadius * 0.78f;
        canvas.drawText("W", cx, cy - offset + labelSize * 0.35f, textPaint);
        canvas.drawText("S", cx, cy + offset + labelSize * 0.35f, textPaint);
        canvas.drawText("A", cx - offset, cy + labelSize * 0.35f, textPaint);
        canvas.drawText("D", cx + offset, cy + labelSize * 0.35f, textPaint);

        // Knob
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(active
                ? Color.argb(Math.min(245, alphaFg + 20), 245, 215, 130)
                : Color.argb(alphaFg, 220, 228, 240));
        canvas.drawCircle(kx, ky, knobRadius, fillPaint);

        strokePaint.setStrokeWidth(Math.max(2f, baseRadius * 0.03f));
        strokePaint.setColor(Color.argb(alphaFg, 255, 255, 255));
        canvas.drawCircle(kx, ky, knobRadius, strokePaint);
    }
}
