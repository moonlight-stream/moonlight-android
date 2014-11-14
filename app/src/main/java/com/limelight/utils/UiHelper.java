package com.limelight.utils;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;

public class UiHelper {

    // Values from https://developer.android.com/training/tv/start/layouts.html
    private static final int TV_VERTICAL_PADDING_DP = 27;
    private static final int TV_HORIZONTAL_PADDING_DP = 48;

    public static void notifyNewRootView(Activity activity)
    {
        View rootView = activity.findViewById(android.R.id.content);
        UiModeManager modeMgr = (UiModeManager) activity.getSystemService(Context.UI_MODE_SERVICE);

        if (modeMgr.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION)
        {
            // Increase view padding on TVs
            float scale = activity.getResources().getDisplayMetrics().density;
            int verticalPaddingPixels = (int) (TV_VERTICAL_PADDING_DP*scale + 0.5f);
            int horizontalPaddingPixels = (int) (TV_HORIZONTAL_PADDING_DP*scale + 0.5f);

            rootView.setPadding(horizontalPaddingPixels, verticalPaddingPixels,
                    horizontalPaddingPixels, verticalPaddingPixels);
        }
    }
}
