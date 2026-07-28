package com.limelight.ui.console;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LauncherLibraryStore {
    private static final String PREFERENCES_NAME = "IrisLauncherLibrary";
    private static final String FAVORITES_PREFIX = "favorites.";
    private static final String RECENTS_PREFIX = "recents.v1.";
    private static final int MAX_RECENTS = 12;

    private final SharedPreferences preferences;

    public LauncherLibraryStore(Context context) {
        this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE));
    }

    LauncherLibraryStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public synchronized boolean toggleFavorite(String hostUuid, int appId) {
        Set<String> ids = new HashSet<>(preferences.getStringSet(
                FAVORITES_PREFIX + hostUuid, Collections.emptySet()));
        String value = Integer.toString(appId);
        boolean favorite;
        if (ids.remove(value)) {
            favorite = false;
        } else {
            ids.add(value);
            favorite = true;
        }
        preferences.edit().putStringSet(FAVORITES_PREFIX + hostUuid, ids).apply();
        return favorite;
    }

    public synchronized boolean isFavorite(String hostUuid, int appId) {
        return preferences.getStringSet(FAVORITES_PREFIX + hostUuid,
                Collections.emptySet()).contains(Integer.toString(appId));
    }

    public synchronized Set<Integer> getFavoriteIds(String hostUuid) {
        Set<Integer> result = new HashSet<>();
        for (String value : preferences.getStringSet(FAVORITES_PREFIX + hostUuid,
                Collections.emptySet())) {
            try {
                result.add(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public synchronized void recordLaunch(String hostUuid, int appId, long launchedAt) {
        LinkedHashMap<Integer, Long> ordered = new LinkedHashMap<>();
        ordered.put(appId, launchedAt);
        for (RecentEntry entry : decodeRecents(preferences.getString(
                RECENTS_PREFIX + hostUuid, "[]"))) {
            if (!ordered.containsKey(entry.appId) && ordered.size() < MAX_RECENTS) {
                ordered.put(entry.appId, entry.lastLaunchedAt);
            }
        }
        preferences.edit().putString(RECENTS_PREFIX + hostUuid, encodeRecents(ordered)).apply();
    }

    public synchronized List<Integer> getRecentAppIds(String hostUuid) {
        List<Integer> result = new ArrayList<>();
        for (RecentEntry entry : decodeRecents(preferences.getString(
                RECENTS_PREFIX + hostUuid, "[]"))) {
            result.add(entry.appId);
        }
        return result;
    }

    public synchronized void prune(String hostUuid, Set<Integer> installedAppIds) {
        Set<String> favoriteIds = new HashSet<>(preferences.getStringSet(
                FAVORITES_PREFIX + hostUuid, Collections.emptySet()));
        Set<String> validFavoriteIds = new HashSet<>();
        for (String value : favoriteIds) {
            try {
                if (installedAppIds.contains(Integer.parseInt(value))) {
                    validFavoriteIds.add(value);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        LinkedHashMap<Integer, Long> recents = new LinkedHashMap<>();
        for (RecentEntry entry : decodeRecents(preferences.getString(
                RECENTS_PREFIX + hostUuid, "[]"))) {
            if (installedAppIds.contains(entry.appId)) {
                recents.put(entry.appId, entry.lastLaunchedAt);
            }
        }

        preferences.edit()
                .putStringSet(FAVORITES_PREFIX + hostUuid, validFavoriteIds)
                .putString(RECENTS_PREFIX + hostUuid, encodeRecents(recents))
                .apply();
    }

    public synchronized void clearHost(String hostUuid) {
        preferences.edit()
                .remove(FAVORITES_PREFIX + hostUuid)
                .remove(RECENTS_PREFIX + hostUuid)
                .apply();
    }

    static List<RecentEntry> decodeRecents(String encoded) {
        List<RecentEntry> result = new ArrayList<>();
        if (encoded == null) {
            return result;
        }

        Set<Integer> seen = new HashSet<>();
        int cursor = 0;
        while (cursor < encoded.length() && result.size() < MAX_RECENTS) {
            int objectStart = encoded.indexOf('{', cursor);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = encoded.indexOf('}', objectStart + 1);
            if (objectEnd < 0) {
                break;
            }
            cursor = objectEnd + 1;

            String object = encoded.substring(objectStart + 1, objectEnd);
            String appIdValue = readNumberField(object, "\"appId\"");
            String launchTimeValue = readNumberField(object, "\"lastLaunchedAt\"");
            if (appIdValue == null || launchTimeValue == null) {
                continue;
            }

            try {
                int appId = Integer.parseInt(appIdValue);
                long launchedAt = Long.parseLong(launchTimeValue);
                if (seen.add(appId)) {
                    result.add(new RecentEntry(appId, launchedAt));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static String readNumberField(String object, String fieldName) {
        int fieldStart = object.indexOf(fieldName);
        if (fieldStart < 0) {
            return null;
        }
        int valueStart = object.indexOf(':', fieldStart + fieldName.length());
        if (valueStart < 0) {
            return null;
        }
        valueStart++;

        while (valueStart < object.length() &&
                Character.isWhitespace(object.charAt(valueStart))) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < object.length() &&
                Character.isDigit(object.charAt(valueEnd))) {
            valueEnd++;
        }
        return valueEnd == valueStart ? null : object.substring(valueStart, valueEnd);
    }

    private static String encodeRecents(LinkedHashMap<Integer, Long> entries) {
        StringBuilder builder = new StringBuilder("[");
        int index = 0;
        for (Integer appId : entries.keySet()) {
            if (index >= MAX_RECENTS) {
                break;
            }
            if (index++ > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.US,
                    "{\"appId\":%d,\"lastLaunchedAt\":%d}",
                    appId, entries.get(appId)));
        }
        return builder.append(']').toString();
    }

    static final class RecentEntry {
        final int appId;
        final long lastLaunchedAt;

        RecentEntry(int appId, long lastLaunchedAt) {
            this.appId = appId;
            this.lastLaunchedAt = lastLaunchedAt;
        }
    }
}
