package com.limelight.customcontrols;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * A single on-screen button rendered from a parsed {@link CustomJsonParser.CustomButton}.
 * Handles multi-touch, toggle mode, and routes input through {@link CustomInputBridge}.
 */
@SuppressLint("ViewConstructor")
public class CustomButtonView extends View {

    private final CustomJsonParser.CustomButton mData;
    private final CustomInputBridge mBridge;

    private boolean mPressed   = false;
    private boolean mToggled   = false;
    private int     mPointerId = -1;
    private boolean mEditMode  = false;

    private final Paint mBgPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEditPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect        = new RectF();

    public CustomButtonView(Context ctx, CustomJsonParser.CustomButton data, CustomInputBridge bridge) {
        super(ctx);
        mData   = data;
        mBridge = bridge;

        mBgPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setColor(Color.WHITE);

        mStrokePaint.setStyle(Paint.Style.STROKE);

        mEditPaint.setStyle(Paint.Style.STROKE);
        mEditPaint.setColor(0xFFFF6600);
        mEditPaint.setStrokeWidth(4f);

        setFocusable(false);
    }

    public CustomJsonParser.CustomButton getData() { return mData; }

    public void setEditMode(boolean edit) {
        mEditMode = edit;
        if (edit && mPressed) release();
        invalidate();
    }

    // ─── Touch ───────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (mEditMode) return false;

        int action    = e.getActionMasked();
        int actionIdx = e.getActionIndex();
        int ptr       = e.getPointerId(actionIdx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mPointerId == -1) { mPointerId = ptr; press(); }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (ptr == mPointerId) {
                    mPointerId = -1;
                    if (mData.isToggle) {
                        mToggled = !mToggled;
                        if (!mToggled) release();
                        // keep pressed visual when toggled on
                        invalidate();
                    } else {
                        release();
                    }
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                mPointerId = -1;
                mToggled   = false;
                release();
                return true;
        }
        return false;
    }

    private void press() {
        mPressed = true;
        mBridge.sendKeycodes(mData.keycodes, true);
        invalidate();
    }

    private void release() {
        mPressed = false;
        mBridge.sendKeycodes(mData.keycodes, false);
        invalidate();
    }

    // ─── Draw ────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // Background colour with opacity
        int rawAlpha = Color.alpha(mData.bgColor);
        int alpha    = Math.round(rawAlpha * mData.opacity);
        boolean highlighted = mPressed || mToggled;
        int color = highlighted ? brighten(mData.bgColor) : mData.bgColor;
        mBgPaint.setColor(color);
        mBgPaint.setAlpha(alpha);

        float radius = cornerRadiusPx(w, h);
        mRect.set(0, 0, w, h);
        canvas.drawRoundRect(mRect, radius, radius, mBgPaint);

        // Stroke
        if (mData.strokeWidth > 0) {
            mStrokePaint.setColor(mData.strokeColor);
            mStrokePaint.setStrokeWidth(mData.strokeWidth * getResources().getDisplayMetrics().density);
            canvas.drawRoundRect(mRect, radius, radius, mStrokePaint);
        }

        // Edit mode outline
        if (mEditMode) {
            canvas.drawRoundRect(mRect, radius, radius, mEditPaint);
        }

        // Label text
        mTextPaint.setTextSize(h * 0.32f);
        float ty = h / 2f - (mTextPaint.ascent() + mTextPaint.descent()) / 2f;
        canvas.drawText(mData.name, w / 2f, ty, mTextPaint);
    }

    private float cornerRadiusPx(int w, int h) {
        return (mData.cornerRadius / 100f) * (Math.min(w, h) / 2f);
    }

    private static int brighten(int color) {
        return Color.argb(
                Color.alpha(color),
                Math.min(255, Color.red(color)   + 70),
                Math.min(255, Color.green(color) + 70),
                Math.min(255, Color.blue(color)  + 70));
    }
}

