package com.limelight.ui.console;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class LibraryShelfProjectorTest {
    private static final class App {
        final int id;
        final String name;
        final boolean running;
        final boolean hidden;

        App(int id, String name, boolean running, boolean hidden) {
            this.id = id;
            this.name = name;
            this.running = running;
            this.hidden = hidden;
        }
    }

    @Test
    public void onlyRunningAppPopulatesContinuePlaying() {
        App alpha = new App(1, "Alpha", false, false);
        App beta = new App(2, "Beta", true, false);
        LibraryShelfProjector.Result<App> result = project(
                Arrays.asList(alpha, beta), Collections.emptySet());

        assertEquals(Collections.singletonList(beta), result.continuePlaying);
    }

    @Test
    public void continuePlayingIsEmptyWhenNothingRuns() {
        App alpha = new App(1, "Alpha", false, false);
        LibraryShelfProjector.Result<App> result = project(
                Collections.singletonList(alpha), Collections.emptySet());

        assertEquals(Collections.emptyList(), result.continuePlaying);
    }

    @Test
    public void favoritesAndAllGamesAreAlphabetical() {
        App zulu = new App(1, "Zulu", false, false);
        App alpha = new App(2, "Alpha", false, false);
        LibraryShelfProjector.Result<App> result = project(
                Arrays.asList(zulu, alpha), new HashSet<>(Arrays.asList(1, 2)));

        assertEquals(Arrays.asList(alpha, zulu), result.favorites);
        assertEquals(Arrays.asList(alpha, zulu), result.allGames);
    }

    @Test
    public void hiddenAppsRespectFullLibraryMode() {
        App visible = new App(1, "Visible", false, false);
        App hidden = new App(2, "Hidden", false, true);

        assertEquals(Collections.singletonList(visible),
                project(Arrays.asList(hidden, visible), Collections.emptySet()).allGames);

        LibraryShelfProjector.Result<App> full = LibraryShelfProjector.project(
                Arrays.asList(hidden, visible), value -> value.id, value -> value.name,
                value -> value.running, value -> value.hidden, true,
                Collections.emptySet());
        assertEquals(Arrays.asList(hidden, visible), full.allGames);
    }

    private LibraryShelfProjector.Result<App> project(
            java.util.List<App> apps, java.util.Set<Integer> favorites) {
        return LibraryShelfProjector.project(
                apps, value -> value.id, value -> value.name,
                value -> value.running, value -> value.hidden, false,
                favorites);
    }
}
