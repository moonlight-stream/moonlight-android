package com.limelight.ui.console;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.view.View;
import android.widget.TextView;

import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;

public final class ConsoleStatusBar {
    private ConsoleStatusBar() {
    }

    public static void enterImmersiveMode(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(activity.getWindow(), decor);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    public static void updateBattery(Context context, TextView batteryView) {
        if (batteryView == null) {
            return;
        }
        Intent battery = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            batteryView.setVisibility(View.GONE);
            return;
        }
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) {
            batteryView.setVisibility(View.GONE);
            return;
        }
        batteryView.setVisibility(View.VISIBLE);
        batteryView.setText(String.format(Locale.US, "%d%%",
                Math.round(level * 100f / scale)));
    }
}
