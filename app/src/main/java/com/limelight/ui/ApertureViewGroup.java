package com.limelight.ui;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

import com.limelight.R;
import com.limelight.utils.UiHelper;


public class ApertureViewGroup extends LinearLayout {

   private int mColor1 = 0;
   private int mColor2 = 0;
   private float mBorderWidth = 0f;
   private float mBorderAngle = 0f;
   private int mDuration = 0;
   private int mMiddleColor = 0;

   private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private RectF rectF;
   private LinearGradient color1;
   private LinearGradient color2;
   private ObjectAnimator animator;
   private float currentSpeed = 0f;

   public ApertureViewGroup(Context context) {
      this(context, null);
   }

   public ApertureViewGroup(Context context, AttributeSet attrs) {
      this(context, attrs, 0);
   }

   public ApertureViewGroup(Context context, AttributeSet attrs, int defStyleAttr) {
      super(context, attrs, defStyleAttr);
      init(context, attrs);
   }

   private void init(Context context, AttributeSet attrs) {
      setOutlineProvider(new ViewOutlineProvider() {
         @Override
         public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mBorderAngle);
         }
      });
      setClipToOutline(true);

      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ApertureViewGroup);
      try {
         mColor1 = a.getColor(R.styleable.ApertureViewGroup_aperture_color1, Color.YELLOW);
         mColor2 = a.getColor(R.styleable.ApertureViewGroup_aperture_color2, -1);
         mBorderWidth = a.getDimension(R.styleable.ApertureViewGroup_aperture_border_width, UiHelper.dpToPx(context, 20));
//         setPadding((int) mBorderWidth / 2,(int) mBorderWidth / 2,(int) mBorderWidth / 2,(int) mBorderWidth / 2);
         mBorderAngle = a.getDimension(R.styleable.ApertureViewGroup_aperture_border_angle, UiHelper.dpToPx(context, 20));
         mDuration = a.getInt(R.styleable.ApertureViewGroup_aperture_duration, 3000);
         mMiddleColor = a.getColor(R.styleable.ApertureViewGroup_aperture_middle_color, Color.BLACK);
      } finally {
         a.recycle();
      }

      animator = ObjectAnimator.ofFloat(this, "currentSpeed", 0f, 360f);
      animator.setRepeatCount(ObjectAnimator.INFINITE);
      animator.setRepeatMode(ObjectAnimator.RESTART);
      animator.setInterpolator(null);
      animator.setDuration(mDuration);
   }

   public float getCurrentSpeed() {
      return currentSpeed;
   }

   public void setCurrentSpeed(float currentSpeed) {
      this.currentSpeed = currentSpeed;
      invalidate();
   }

   @Override
   protected void onSizeChanged(int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      if (rectF == null) {
         float left = 0f + mBorderWidth / 2f;
         float top = 0f + mBorderWidth / 2f;
         float right = left + w - mBorderWidth;
         float bottom = top + h - mBorderWidth;
         rectF = new RectF(left, top, right, bottom);
      }

      if (color1 == null) {
         color1 = new LinearGradient(
                 w * 1f, h / 2f,
                 w * 1f, h * 1f,
                 new int[]{Color.TRANSPARENT, mColor1},
                 new float[]{0f, 0.9f},
                 Shader.TileMode.CLAMP
         );
      }

      if (color2 == null && mColor2 != -1) {
         color2 = new LinearGradient(
                 w / 2f, h / 2f,
                 w / 2f, 0f,
                 new int[]{Color.TRANSPARENT, mColor2},
                 new float[]{0f, 0.9f},
                 Shader.TileMode.CLAMP
         );
      }

      animator.start();
   }

   @Override
   protected void dispatchDraw(Canvas canvas) {
      float left1 = getWidth() / 2f;
      float top1 = getHeight() / 2f;
      float right1 = left1 + getWidth();
      float bottom1 = top1 + getHeight();

      canvas.save();
      canvas.rotate(currentSpeed, getWidth() / 2f, getHeight() / 2f);

      paint.setShader(color1);
      canvas.drawRect(left1, top1, right1, bottom1, paint);
      paint.setShader(null);

      if (mColor2 != -1) {
         paint.setShader(color2);
         canvas.drawRect(left1, top1, -right1, -bottom1, paint);
         paint.setShader(null);
      }

      paint.setColor(mMiddleColor);
      canvas.drawRoundRect(rectF, mBorderAngle, mBorderAngle, paint);

      canvas.restore();

      super.dispatchDraw(canvas);
   }
}
