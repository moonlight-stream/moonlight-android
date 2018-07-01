package com.limelight.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;

import com.limelight.AppView;
import com.limelight.ShortcutTrampoline;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ShortcutHelper {

    private final ShortcutManager sm;
    private final Context context;

    public ShortcutHelper(Context context) {
        this.context = context;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            sm = context.getSystemService(ShortcutManager.class);
        }
        else {
            sm = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private void reapShortcutsForDynamicAdd() {
        List<ShortcutInfo> dynamicShortcuts = sm.getDynamicShortcuts();
        while (dynamicShortcuts.size() >= sm.getMaxShortcutCountPerActivity()) {
            ShortcutInfo maxRankShortcut = dynamicShortcuts.get(0);
            for (ShortcutInfo scut : dynamicShortcuts) {
                if (maxRankShortcut.getRank() < scut.getRank()) {
                    maxRankShortcut = scut;
                }
            }
            sm.removeDynamicShortcuts(Collections.singletonList(maxRankShortcut.getId()));
        }
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private List<ShortcutInfo> getAllShortcuts() {
        LinkedList<ShortcutInfo> list = new LinkedList<>();
        list.addAll(sm.getDynamicShortcuts());
        list.addAll(sm.getPinnedShortcuts());
        return list;
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private ShortcutInfo getInfoForId(String id) {
        List<ShortcutInfo> shortcuts = getAllShortcuts();

        for (ShortcutInfo info : shortcuts) {
            if (info.getId().equals(id)) {
                return info;
            }
        }

        return null;
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private boolean isExistingDynamicShortcut(String id) {
        for (ShortcutInfo si : sm.getDynamicShortcuts()) {
            if (si.getId().equals(id)) {
                return true;
            }
        }

        return false;
    }

    public void reportShortcutUsed(String id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            if (getInfoForId(id) != null) {
                sm.reportShortcutUsed(id);
            }
        }
    }

    public void createAppViewShortcut(String id, String computerName, String computerUuid, boolean forceAdd) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Intent i = new Intent(context, ShortcutTrampoline.class);
            i.putExtra(AppView.NAME_EXTRA, computerName);
            i.putExtra(AppView.UUID_EXTRA, computerUuid);
            i.setAction(Intent.ACTION_DEFAULT);

            ShortcutInfo sinfo = new ShortcutInfo.Builder(context, id)
                    .setIntent(i)
                    .setShortLabel(computerName)
                    .setLongLabel(computerName)
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_pc_scut))
                    .build();

            ShortcutInfo existingSinfo = getInfoForId(id);
            if (existingSinfo != null) {
                // Update in place
                sm.updateShortcuts(Collections.singletonList(sinfo));
                sm.enableShortcuts(Collections.singletonList(id));
            }

            // Reap shortcuts to make space for this if it's new
            // NOTE: This CAN'T be an else on the above if, because it's
            // possible that we have an existing shortcut but it's not a dynamic one.
            if (!isExistingDynamicShortcut(id)) {
                // To avoid a random carousel of shortcuts popping in and out based on polling status,
                // we only add shortcuts if it's not at the limit or the user made a conscious action
                // to interact with this PC.
                if (forceAdd || sm.getDynamicShortcuts().size() < sm.getMaxShortcutCountPerActivity()) {
                    reapShortcutsForDynamicAdd();
                    sm.addDynamicShortcuts(Collections.singletonList(sinfo));
                }
            }
        }
    }

    public void createAppViewShortcut(String id, ComputerDetails details, boolean forceAdd) {
        createAppViewShortcut(id, details.name, details.uuid.toString(), forceAdd);
    }

    @TargetApi(Build.VERSION_CODES.O)
    public boolean createPinnedGameShortcut(String id, Bitmap iconBits, String computerName, String computerUuid, String appName, String appId) {
        if (sm.isRequestPinShortcutSupported()) {
            Icon appIcon;
            Intent i = new Intent(context, ShortcutTrampoline.class);

            i.putExtra(AppView.NAME_EXTRA, computerName);
            i.putExtra(AppView.UUID_EXTRA, computerUuid);
            i.putExtra(ShortcutTrampoline.APP_ID_EXTRA, appId);
            i.setAction(Intent.ACTION_DEFAULT);

            if(iconBits != null) {
                appIcon = Icon.createWithAdaptiveBitmap(iconBits);
            } else {
                appIcon = Icon.createWithResource(context, R.mipmap.ic_pc_scut);
            }

            ShortcutInfo sInfo = new ShortcutInfo.Builder(context, id)
                .setIntent(i)
                .setShortLabel(appName + " (" + computerName + ")")
                .setIcon(appIcon)
                .build();

            return sm.requestPinShortcut(sInfo, null);
        } else {
            return false;
        }
    }

    public boolean createPinnedGameShortcut(String id, Bitmap iconBits, ComputerDetails cDetails, NvApp app) {
        return createPinnedGameShortcut(id, iconBits, cDetails.name, cDetails.uuid.toString(), app.getAppName(), Integer.valueOf(app.getAppId()).toString());
    }

    public void disableShortcut(String id, CharSequence reason) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            if (getInfoForId(id) != null) {
                sm.disableShortcuts(Collections.singletonList(id), reason);
            }
        }
    }
}
