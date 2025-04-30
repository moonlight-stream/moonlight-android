package com.limelight.grid.assets;

import android.graphics.Bitmap;

public class ScaledBitmap {
    public int originalWidth;
    public int originalHeight;

    public Bitmap bitmap;

    public ScaledBitmap() {}

    public ScaledBitmap(int originalWidth, int originalHeight, Bitmap bitmap) {
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        this.bitmap = bitmap;
    }
}
