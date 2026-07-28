package com.limelight.utils;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.R;

public class SpinnerDialog implements Runnable, OnCancelListener {
    private final String title;
    private final String message;
    private final Activity activity;
    private Dialog progress;
    private TextView messageView;
    private final boolean finish;

    private static final ArrayList<SpinnerDialog> rundownDialogs = new ArrayList<>();

    private SpinnerDialog(Activity activity, String title, String message, boolean finish) {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.finish = finish;
    }

    public static SpinnerDialog displayDialog(Activity activity, String title,
                                               String message, boolean finish) {
        SpinnerDialog spinner = new SpinnerDialog(activity, title, message, finish);
        activity.runOnUiThread(spinner);
        return spinner;
    }

    public static void closeDialogs(Activity activity) {
        synchronized (rundownDialogs) {
            Iterator<SpinnerDialog> iterator = rundownDialogs.iterator();
            while (iterator.hasNext()) {
                SpinnerDialog dialog = iterator.next();
                if (dialog.activity == activity) {
                    iterator.remove();
                    if (dialog.progress != null && dialog.progress.isShowing()) {
                        dialog.progress.dismiss();
                    }
                }
            }
        }
    }

    public void dismiss() {
        activity.runOnUiThread(this);
    }

    public void setMessage(final String message) {
        activity.runOnUiThread(() -> {
            if (messageView != null) {
                messageView.setText(message);
            }
        });
    }

    @Override
    public void run() {
        if (activity.isFinishing()) {
            return;
        }

        if (progress == null) {
            progress = new Dialog(activity);
            LinearLayout panel = new LinearLayout(activity);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setGravity(Gravity.CENTER_HORIZONTAL);
            panel.setPadding(dp(30), dp(26), dp(30), dp(26));
            panel.setBackgroundResource(R.drawable.iris_glass_panel);

            ProgressBar spinner = new ProgressBar(activity);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(
                    activity.getResources().getColor(R.color.iris_cyan)));
            panel.addView(spinner, new LinearLayout.LayoutParams(dp(52), dp(52)));

            TextView titleView = new TextView(activity);
            titleView.setText(title);
            titleView.setTextSize(22);
            titleView.setTextColor(activity.getResources().getColor(R.color.iris_text_primary));
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(0, dp(16), 0, dp(6));
            panel.addView(titleView);

            messageView = new TextView(activity);
            messageView.setText(message);
            messageView.setTextSize(16);
            messageView.setTextColor(activity.getResources().getColor(R.color.iris_text_secondary));
            messageView.setGravity(Gravity.CENTER);
            panel.addView(messageView);

            progress.setContentView(panel);
            progress.setOnCancelListener(this);
            progress.setCancelable(finish);
            progress.setCanceledOnTouchOutside(false);
            progress.show();

            Window window = progress.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(480),
                        activity.getResources().getDisplayMetrics().widthPixels - dp(36));
                params.dimAmount = 0.72f;
                window.setAttributes(params);
            }

            synchronized (rundownDialogs) {
                rundownDialogs.add(this);
            }
        }
        else {
            synchronized (rundownDialogs) {
                if (rundownDialogs.remove(this) && progress.isShowing()) {
                    progress.dismiss();
                }
            }
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        synchronized (rundownDialogs) {
            rundownDialogs.remove(this);
        }
        activity.finish();
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
