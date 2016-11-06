package com.limelight.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.limelight.HelpActivity;

public class HelpLauncher {

    private static void launchUrl(Context context, String url) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));

        // Try to launch the default browser
        try {
            context.startActivity(i);
        } catch (ActivityNotFoundException e) {
            // This platform has no browser (possibly a leanback device)
            // We'll launch our WebView activity
            i = new Intent(context, HelpActivity.class);
            i.setData(Uri.parse(url));
            context.startActivity(i);
        }
    }

    public static void launchSetupGuide(Context context) {
        launchUrl(context, "https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide");
    }

    public static void launchTroubleshooting(Context context) {
        launchUrl(context, "https://github.com/moonlight-stream/moonlight-docs/wiki/Troubleshooting");
    }
}
