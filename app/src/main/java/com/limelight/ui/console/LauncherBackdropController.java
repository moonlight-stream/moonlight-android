package com.limelight.ui.console;

import android.app.ActivityManager;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.widget.ImageView;

public final class LauncherBackdropController {
    private static final long CROSSFADE_MS = 420;

    private final ImageView first;
    private final ImageView second;
    private ImageView visible;
    private ImageView hidden;
    private final boolean blurSupported;

    public LauncherBackdropController(Context context, ImageView first, ImageView second) {
        this.first = first;
        this.second = second;
        visible = first;
        hidden = second;
        first.setAlpha(0f);
        second.setAlpha(0f);

        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        blurSupported = LauncherEffectPolicy.allowBlur(Build.VERSION.SDK_INT,
                manager != null && manager.isLowRamDevice(), true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurSupported) {
            applyBlur(first, second);
        }
    }

    @TargetApi(Build.VERSION_CODES.S)
    private static void applyBlur(ImageView first, ImageView second) {
        RenderEffect effect = RenderEffect.createBlurEffect(
                32f, 32f, Shader.TileMode.CLAMP);
        first.setRenderEffect(effect);
        second.setRenderEffect(effect);
    }

    public void show(Bitmap bitmap, boolean animate) {
        if (bitmap == null) {
            clear(animate);
            return;
        }

        hidden.animate().cancel();
        visible.animate().cancel();
        hidden.setImageBitmap(bitmap);
        hidden.setAlpha(0f);

        long duration = animate ? CROSSFADE_MS : 0;
        ImageView oldVisible = visible;
        ImageView newVisible = hidden;
        hidden.animate().alpha(0.34f).setDuration(duration).start();
        oldVisible.animate().alpha(0f).setDuration(duration).withEndAction(() ->
                oldVisible.setImageDrawable(null)).start();

        visible = newVisible;
        hidden = oldVisible;
    }

    public void clear(boolean animate) {
        first.animate().cancel();
        second.animate().cancel();
        long duration = animate ? 180 : 0;
        first.animate().alpha(0f).setDuration(duration).start();
        second.animate().alpha(0f).setDuration(duration).start();
    }

    public void release() {
        first.animate().cancel();
        second.animate().cancel();
        first.setImageDrawable(null);
        second.setImageDrawable(null);
    }
}
