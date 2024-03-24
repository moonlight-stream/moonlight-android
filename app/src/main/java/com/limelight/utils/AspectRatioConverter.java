package com.limelight.utils;

import android.util.Log;

public class AspectRatioConverter {
    public static String getAspectRatio(int width, int height) {
        float ratio = (float) width / height;
        float truncatedValue = (float) (Math.floor(ratio * 100) / 100);

        if (truncatedValue == 1.25f) return "5:4";
        if (truncatedValue == 1.33f) return "4:3";
        if (truncatedValue == 1.50f) return "3:2";
        if (truncatedValue == 1.60f) return "16:10";
        if (truncatedValue == 1.77f) return "16:9";
        if (truncatedValue == 1.85f) return "1.85:1";
        if (truncatedValue == 2.22f) return "20:9";
        if (truncatedValue >= 2.37f && truncatedValue <= 2.44f) return "21:9";
        if (truncatedValue == 2.76f) return "2.76:1";
        if (truncatedValue == 3.20f) return "32:10";
        if (truncatedValue == 3.55f) return "32:9";

        return null;
    }
}
