package com.limelight.wincaster;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.limelight.LimeLog;

public class WinCasterBootReceiver extends BroadcastReceiver {
    private static final String TAG = "WinCasterBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        LimeLog.info(TAG + ": Received action: " + action);

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
            case "com.htc.intent.action.QUICKBOOT_POWERON":
                startWinCasterService(context);
                break;
            default:
                LimeLog.info(TAG + ": Ignoring unknown action: " + action);
                break;
        }
    }

    private void startWinCasterService(Context context) {
        LimeLog.info(TAG + ": Starting WinCasterCommandService on boot");

        Intent serviceIntent = new Intent(context, WinCasterCommandService.class);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            LimeLog.info(TAG + ": WinCasterCommandService started successfully");
        } catch (Exception e) {
            LimeLog.warning(TAG + ": Failed to start WinCasterCommandService: " + e.getMessage());
        }
    }
}
