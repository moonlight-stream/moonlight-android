package com.limelight.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import com.limelight.HelpActivity;

public class HelpLauncher {

    private static void launchUrl(Context context, String url) {
        // Try to launch the default browser
        try {
            // Fire TV devices will lie and say they do have a browser
            // even though the OS just shows an error dialog if we
            // try to use it.
            if (!"Amazon".equalsIgnoreCase(Build.MANUFACTURER)) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                context.startActivity(i);
                return;
            }
        } catch (Exception e) {
            // This is only supposed to throw ActivityNotFoundException but
            // it can (at least) also throw SecurityException if a user's default
            // browser is not exported. We'll catch everything to workaround this.

            // Fall through
        }

        // This platform has no browser (possibly a leanback device)
        // We'll launch our WebView activity
        Intent i = new Intent(context, HelpActivity.class);
        i.setData(Uri.parse(url));
        context.startActivity(i);
    }

    public static void launchSetupGuide(Context context) {
        launchUrl(context, "https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide");
    }

    public static void launchTroubleshooting(Context context) {
        launchUrl(context, "https://github.com/moonlight-stream/moonlight-docs/wiki/Troubleshooting");
    }
}
