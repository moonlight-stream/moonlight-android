package com.limelight.utils;

import android.app.Activity;
import android.graphics.Bitmap;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ShortcutHelper {

    private final Activity context;
    private final TvChannelHelper tvChannelHelper;

    public ShortcutHelper(Activity context) {
        this.context = context;
        this.tvChannelHelper = new TvChannelHelper(context);
    }

    private void reapShortcutsForDynamicAdd() {
        List<ShortcutInfoCompat> dynamicShortcuts = ShortcutManagerCompat.getDynamicShortcuts(context);
        while (!dynamicShortcuts.isEmpty() && dynamicShortcuts.size()
                >= ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        ) {
            ShortcutInfoCompat maxRankShortcut = dynamicShortcuts.get(0);
            for (ShortcutInfoCompat scut : dynamicShortcuts) {
                if (maxRankShortcut.getRank() < scut.getRank()) {
                    maxRankShortcut = scut;
                }
            }
            ShortcutManagerCompat.removeDynamicShortcuts(
                    context, Collections.singletonList(maxRankShortcut.getId()));
        }
    }

    private List<ShortcutInfoCompat> getAllShortcuts() {
        return ShortcutManagerCompat.getShortcuts(context,
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC
                        | ShortcutManagerCompat.FLAG_MATCH_PINNED);
    }

    private ShortcutInfoCompat getInfoForId(String id) {
        List<ShortcutInfoCompat> shortcuts = getAllShortcuts();

        for (ShortcutInfoCompat info : shortcuts) {
            if (info.getId().equals(id)) {
                return info;
            }
        }

        return null;
    }

    private boolean isExistingDynamicShortcut(String id) {
        for (ShortcutInfoCompat si : ShortcutManagerCompat.getDynamicShortcuts(context)) {
            if (si.getId().equals(id)) {
                return true;
            }
        }

        return false;
    }

    public void reportComputerShortcutUsed(ComputerDetails computer) {
        if (getInfoForId(computer.uuid) != null) {
            ShortcutManagerCompat.reportShortcutUsed(context, computer.uuid);
        }
    }

    public void reportGameLaunched(ComputerDetails computer, NvApp app) {
        tvChannelHelper.createTvChannel(computer);
        tvChannelHelper.addGameToChannel(computer, app);
    }

    public void createAppViewShortcut(ComputerDetails computer, boolean forceAdd, boolean newlyPaired) {
        ShortcutInfoCompat sinfo = new ShortcutInfoCompat.Builder(context, computer.uuid)
                .setIntent(ServerHelper.createPcShortcutIntent(context, computer))
                .setShortLabel(computer.name)
                .setLongLabel(computer.name)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_pc_scut))
                .build();

        ShortcutInfoCompat existingSinfo = getInfoForId(computer.uuid);
        if (existingSinfo != null) {
            // Update in place
            ShortcutManagerCompat.updateShortcuts(context, Collections.singletonList(sinfo));
            ShortcutManagerCompat.enableShortcuts(context, Collections.singletonList(sinfo));
        }

        // Reap shortcuts to make space for this if it's new
        // NOTE: This CAN'T be an else on the above if, because it's
        // possible that we have an existing shortcut but it's not a dynamic one.
        if (!isExistingDynamicShortcut(computer.uuid)) {
            // To avoid a random carousel of shortcuts popping in and out based on polling status,
            // we only add shortcuts if it's not at the limit or the user made a conscious action
            // to interact with this PC.

            if (forceAdd) {
                // This should free an entry for us to add one below
                reapShortcutsForDynamicAdd();
            }

            // We still need to check the maximum shortcut count even after reaping,
            // because there's a possibility that it could be zero.
            if (ShortcutManagerCompat.getDynamicShortcuts(context).size()
                    < ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)) {
                // Add a shortcut if there is room
                ShortcutManagerCompat.addDynamicShortcuts(context, Collections.singletonList(sinfo));
            }
        }

        if (newlyPaired) {
            // Avoid hammering the channel API for each computer poll because it will throttle us
            tvChannelHelper.createTvChannel(computer);
            tvChannelHelper.requestChannelOnHomeScreen(computer);
        }
    }

    public void createAppViewShortcutForOnlineHost(ComputerDetails details) {
        createAppViewShortcut(details, false, false);
    }

    private String getShortcutIdForGame(ComputerDetails computer, NvApp app) {
        return computer.uuid + app.getAppId();
    }

    public boolean createPinnedGameShortcut(ComputerDetails computer, NvApp app, Bitmap iconBits) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            IconCompat appIcon;

            if (iconBits != null) {
                appIcon = IconCompat.createWithAdaptiveBitmap(iconBits);
            } else {
                appIcon = IconCompat.createWithResource(context, R.mipmap.ic_pc_scut);
            }

            ShortcutInfoCompat sInfo =
                    new ShortcutInfoCompat.Builder(context, getShortcutIdForGame(computer, app))
                            .setIntent(ServerHelper.createAppShortcutIntent(context, computer, app))
                            .setShortLabel(app.getAppName() + " (" + computer.name + ")")
                            .setIcon(appIcon)
                            .build();

            return ShortcutManagerCompat.requestPinShortcut(context, sInfo, null);
        } else {
            return false;
        }
    }

    public void disableComputerShortcut(ComputerDetails computer, CharSequence reason) {
        tvChannelHelper.deleteChannel(computer);
        // Delete the computer shortcut itself
        if (getInfoForId(computer.uuid) != null) {
            ShortcutManagerCompat.disableShortcuts(context, Collections.singletonList(computer.uuid), reason);
        }

        // Delete all associated app shortcuts too
        List<ShortcutInfoCompat> shortcuts = getAllShortcuts();
        LinkedList<String> appShortcutIds = new LinkedList<>();
        for (ShortcutInfoCompat info : shortcuts) {
            if (info.getId().startsWith(computer.uuid)) {
                appShortcutIds.add(info.getId());
            }
        }
        ShortcutManagerCompat.disableShortcuts(context, appShortcutIds, reason);
    }

    public void disableAppShortcut(ComputerDetails computer, NvApp app, CharSequence reason) {
        tvChannelHelper.deleteProgram(computer, app);
        String id = getShortcutIdForGame(computer, app);
        if (getInfoForId(id) != null) {
            ShortcutManagerCompat.disableShortcuts(context, Collections.singletonList(id), reason);
        }
    }

    public void enableAppShortcut(ComputerDetails computer, NvApp app) {
        String id = getShortcutIdForGame(computer, app);
        if (getInfoForId(id) != null) {
            ShortcutManagerCompat.enableShortcuts(
                    context, Collections.singletonList(getInfoForId(id)));
        }
    }
}
