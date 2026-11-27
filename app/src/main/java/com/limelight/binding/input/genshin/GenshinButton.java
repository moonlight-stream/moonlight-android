package com.limelight.binding.input.genshin;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Circular vector-drawn touch button for the Genshin overlay.
 * Renders original geometric/text glyphs (no HoYoverse assets), press highlight/scale feedback,
 * and a small PC key badge so the user always sees what PC key/mouse button is sent.
 */
public class GenshinButton {

    public final GenshinMapping.Action action;
    public GenshinProfile.ElementLayout layout;

    // Runtime pixel geometry computed from safeRect
    public float cx;
    public float cy;
    public float radius;

    // Touch state
    public int activePointerId = -1;
    public boolean pressed = false;
    public boolean toggledActive = false;
    public boolean longPressTriggered = false;
    public boolean visible = true;

    public GenshinButton(GenshinMapping.Action action, GenshinProfile.ElementLayout layout) {
        this.action = action;
        this.layout = layout;
    }

    public void updatePixelBounds(RectF safeRect, float globalSizeScale) {
        float w = safeRect.width();
        float h = safeRect.height();
        this.cx = safeRect.left + layout.centerX * w;
        this.cy = safeRect.top + layout.centerY * h;
        float diameter = layout.sizePct * h * globalSizeScale;
        this.radius = Math.max(18f, diameter * 0.5f);
    }

    public boolean hitTest(float x, float y) {
        if (!visible) {
            return false;
        }
        float dx = x - cx;
        float dy = y - cy;
        // Slightly generous hit radius (+10%) for fast combat taps
        float r = radius * 1.10f;
        return (dx * dx + dy * dy) <= (r * r);
    }

    public void draw(Canvas canvas, Paint fillPaint, Paint strokePaint, Paint textPaint,
                     int globalOpacity, boolean editMode, boolean selected, boolean sprintUseRmb) {
        if (!visible) {
            return;
        }

        int effOpacity = (layout.opacity >= 0) ? layout.opacity : globalOpacity;
        int alphaBg = Math.round(effOpacity * 1.55f);     // max ~155 for translucent glass look
        int alphaFg = Math.round(effOpacity * 2.45f);     // crisp border & label
        alphaBg = Math.max(25, Math.min(220, alphaBg));
        alphaFg = Math.max(50, Math.min(255, alphaFg));

        float drawRadius = pressed ? radius * 0.91f : radius;

        // Accent colors per action role (original neutral/gold/cyan palette)
        int accentColor = getAccentColor();
        int fillColor;
        if (pressed || toggledActive) {
            fillColor = Color.argb(Math.min(230, alphaBg + 60),
                    Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        } else {
            fillColor = Color.argb(alphaBg, 22, 26, 36);
        }

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
        canvas.drawCircle(cx, cy, drawRadius, fillPaint);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(2f, drawRadius * 0.075f));
        if (editMode && selected) {
            strokePaint.setColor(Color.argb(255, 80, 255, 120));
        } else if (editMode) {
            strokePaint.setColor(Color.argb(220, 255, 190, 60));
        } else if (pressed || toggledActive) {
            strokePaint.setColor(Color.argb(255, 255, 255, 255));
        } else {
            strokePaint.setColor(Color.argb(alphaFg,
                    Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        }
        canvas.drawCircle(cx, cy, drawRadius, strokePaint);

        // Main label
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.argb(alphaFg, 245, 247, 252));
        float mainTextSize = drawRadius * (action.label.length() <= 2 ? 0.62f : 0.44f);
        textPaint.setTextSize(mainTextSize);
        textPaint.setFakeBoldText(true);
        float textY = cy + mainTextSize * 0.18f;
        canvas.drawText(action.label, cx, textY, textPaint);

        // Small PC key badge at bottom of circle
        String badge = (action == GenshinMapping.Action.SPRINT && sprintUseRmb)
                ? "RMB"
                : action.keyBadge;
        float badgeTextSize = Math.max(10f, drawRadius * 0.29f);
        textPaint.setTextSize(badgeTextSize);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(Color.argb(Math.min(255, alphaFg), 255, 225, 140));
        canvas.drawText(badge, cx, cy + drawRadius * 0.68f, textPaint);

        // Long-press hint dot on character slots (Alt+1..4)
        if (action.inputType == GenshinMapping.InputType.CHARACTER_SLOT) {
            float hintSize = Math.max(8f, drawRadius * 0.24f);
            textPaint.setTextSize(hintSize);
            textPaint.setColor(Color.argb(alphaFg, 160, 225, 255));
            canvas.drawText("ALT+Q", cx, cy - drawRadius * 0.42f, textPaint);
        }
    }

    private int getAccentColor() {
        switch (action) {
            case ATTACK:
                return Color.rgb(245, 200, 90);
            case SKILL_E:
                return Color.rgb(95, 205, 245);
            case BURST_Q:
                return Color.rgb(255, 155, 70);
            case SPRINT:
                return Color.rgb(120, 230, 175);
            case JUMP:
                return Color.rgb(175, 210, 255);
            case CHAR_1:
            case CHAR_2:
            case CHAR_3:
            case CHAR_4:
                return Color.rgb(210, 185, 255);
            default:
                return Color.rgb(200, 210, 225);
        }
    }
}
