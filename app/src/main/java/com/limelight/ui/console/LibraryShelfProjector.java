package com.limelight.ui.console;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class LibraryShelfProjector {
    public interface IdReader<T> {
        int getId(T value);
    }

    public interface NameReader<T> {
        String getName(T value);
    }

    public interface FlagReader<T> {
        boolean get(T value);
    }

    private LibraryShelfProjector() {
    }

    public static <T> Result<T> project(
            List<T> installedApps,
            IdReader<T> id,
            NameReader<T> name,
            FlagReader<T> running,
            FlagReader<T> hidden,
            boolean showHidden,
            Set<Integer> favoriteIds) {
        List<T> all = new ArrayList<>();
        for (T app : installedApps) {
            if (showHidden || !hidden.get(app)) {
                all.add(app);
            }
        }
        Collections.sort(all, new Comparator<T>() {
            @Override
            public int compare(T left, T right) {
                return name.getName(left).compareToIgnoreCase(name.getName(right));
            }
        });

        List<T> favorites = new ArrayList<>();
        for (T app : all) {
            if (favoriteIds.contains(id.getId(app))) {
                favorites.add(app);
            }
        }

        List<T> recent = new ArrayList<>();
        for (T app : all) {
            if (running.get(app)) {
                recent.add(app);
                break;
            }
        }

        return new Result<>(recent, favorites, all);
    }

    public static final class Result<T> {
        public final List<T> continuePlaying;
        public final List<T> favorites;
        public final List<T> allGames;

        Result(List<T> continuePlaying, List<T> favorites, List<T> allGames) {
            this.continuePlaying = Collections.unmodifiableList(continuePlaying);
            this.favorites = Collections.unmodifiableList(favorites);
            this.allGames = Collections.unmodifiableList(allGames);
        }
    }
}
