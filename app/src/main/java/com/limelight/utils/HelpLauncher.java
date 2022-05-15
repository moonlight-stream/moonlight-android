package com.limelight.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.limelight.HelpActivity;

public class HelpLauncher {
    public static void launchUrl(Context context, String url) {
        // Try to launch the default browser
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));

            // Several Android TV devices will lie and say they do have a browser even though the OS
            // just shows an error dialog if we try to use it. We used to try to be clever and check
            // the package name of the resolved intent, but it's not worth it anymore with Android 11's
            // package visibility changes. We'll just always use the WebView on Android TV.
            if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
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
