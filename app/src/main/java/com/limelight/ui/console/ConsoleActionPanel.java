package com.limelight.ui.console;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

import java.util.List;

public final class ConsoleActionPanel {
    public interface Listener {
        void onAction(int actionId);
    }

    public static final class Action {
        public final int id;
        public final CharSequence label;
        public final boolean destructive;

        public Action(int id, CharSequence label) {
            this(id, label, false);
        }

        public Action(int id, CharSequence label, boolean destructive) {
            this.id = id;
            this.label = label;
            this.destructive = destructive;
        }
    }

    private ConsoleActionPanel() {
    }

    public static void show(Activity activity, CharSequence title,
                            List<Action> actions, Listener listener) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 24), dp(activity, 22),
                dp(activity, 24), dp(activity, 22));
        panel.setBackgroundResource(R.drawable.iris_glass_dark_panel);

        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(activity.getResources().getColor(R.color.iris_text_primary));
        heading.setTextSize(24);
        heading.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        heading.setPadding(dp(activity, 12), 0, dp(activity, 12), dp(activity, 14));
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View firstAction = null;
        for (Action action : actions) {
            TextView row = new TextView(activity);
            row.setText(action.label);
            row.setTextColor(activity.getResources().getColor(
                    action.destructive ? R.color.iris_error : R.color.iris_text_primary));
            row.setTextSize(17);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setMinHeight(dp(activity, 54));
            row.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
            row.setBackgroundResource(R.drawable.iris_button_selector);
            row.setOnClickListener(view -> {
                dialog.dismiss();
                listener.onAction(action.id);
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (firstAction != null) {
                rowParams.topMargin = dp(activity, 8);
            }
            panel.addView(row, rowParams);
            if (firstAction == null) {
                firstAction = row;
            }
        }

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            boolean landscape = activity.getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_LANDSCAPE;
            params.width = landscape ? dp(activity, 430) :
                    WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = landscape ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.BOTTOM;
            params.dimAmount = 0.72f;
            window.setAttributes(params);
        }

        boolean reducedMotion = LauncherUiPreferences.read(activity).reducedMotion;
        panel.setAlpha(0f);
        panel.setTranslationX(reducedMotion ? 0 : dp(activity, 26));
        panel.animate().alpha(1f).translationX(0).setDuration(reducedMotion ? 90 : 220).start();
        if (firstAction != null) {
            firstAction.requestFocus();
        }
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
