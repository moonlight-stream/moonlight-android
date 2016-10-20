package com.limelight.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Build;

import com.limelight.AppView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class ShortcutHelper {

    private final ShortcutManager sm;
    private final Context context;

    public ShortcutHelper(Context context) {
        this.context = context;
        if (Build.VERSION.SDK_INT >= 25) {
            sm = context.getSystemService(ShortcutManager.class);
        }
        else {
            sm = null;
        }
    }

    @TargetApi(25)
    private void reapShortcutsForDynamicAdd() {
        List<ShortcutInfo> dynamicShortcuts = sm.getDynamicShortcuts();
        while (dynamicShortcuts.size() >= sm.getMaxShortcutCountPerActivity()) {
            ShortcutInfo maxRankShortcut = dynamicShortcuts.get(0);
            for (ShortcutInfo scut : dynamicShortcuts) {
                if (maxRankShortcut.getRank() < scut.getRank()) {
                    maxRankShortcut = scut;
                }
            }
            sm.removeDynamicShortcuts(Arrays.asList(maxRankShortcut.getId()));
        }
    }

    @TargetApi(25)
    private List<ShortcutInfo> getAllShortcuts() {
        LinkedList<ShortcutInfo> list = new LinkedList<>();
        list.addAll(sm.getDynamicShortcuts());
        list.addAll(sm.getPinnedShortcuts());
        return list;
    }

    @TargetApi(25)
    private ShortcutInfo getInfoForId(String id) {
        List<ShortcutInfo> shortcuts = getAllShortcuts();

        for (ShortcutInfo info : shortcuts) {
            if (info.getId().equals(id)) {
                return info;
            }
        }

        return null;
    }

    public void reportShortcutUsed(String id) {
        if (Build.VERSION.SDK_INT >= 25) {
            ShortcutInfo sinfo = getInfoForId(id);
            if (sinfo != null) {
                sm.reportShortcutUsed(id);
            }
        }
    }

    public void createAppViewShortcut(String id, ComputerDetails details) {
        if (Build.VERSION.SDK_INT >= 25) {
            Intent i = new Intent(context, AppView.class);
            i.putExtra(AppView.NAME_EXTRA, details.name);
            i.putExtra(AppView.UUID_EXTRA, details.uuid.toString());
            i.putExtra(AppView.SHORTCUT_EXTRA, true);
            i.setAction(Intent.ACTION_DEFAULT);

            ShortcutInfo sinfo = new ShortcutInfo.Builder(context, id)
                    .setIntent(i)
                    .setShortLabel(details.name)
                    .setLongLabel(details.name)
                    .build();

            ShortcutInfo existingSinfo = getInfoForId(id);
            if (existingSinfo != null) {
                // Update in place
                sm.updateShortcuts(Arrays.asList(sinfo));
                sm.enableShortcuts(Arrays.asList(id));
            }
            else {
                // Reap shortcuts to make space for this new one
                reapShortcutsForDynamicAdd();

                // Add the new shortcut
                sm.addDynamicShortcuts(Arrays.asList(sinfo));
            }
        }
    }

    public void disableShortcut(String id, CharSequence reason) {
        if (Build.VERSION.SDK_INT >= 25) {
            ShortcutInfo sinfo = getInfoForId(id);
            if (sinfo != null) {
                sm.disableShortcuts(Arrays.asList(id), reason);
            }
        }
    }
}
