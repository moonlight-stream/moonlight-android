package com.limelight.ui.console;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LauncherLibraryStoreCodecTest {
    @Test
    public void malformedJsonRecoversAsEmpty() {
        assertTrue(LauncherLibraryStore.decodeRecents("not-json").isEmpty());
        assertTrue(LauncherLibraryStore.decodeRecents(null).isEmpty());
        assertTrue(LauncherLibraryStore.decodeRecents(
                "[{\"appId\":3},{\"lastLaunchedAt\":30}]").isEmpty());
    }

    @Test
    public void recentsRemainOrderedAndDeduplicated() {
        List<LauncherLibraryStore.RecentEntry> result =
                LauncherLibraryStore.decodeRecents(
                        "[{\"appId\":3,\"lastLaunchedAt\":30}," +
                        "{\"appId\":1,\"lastLaunchedAt\":20}," +
                        "{\"appId\":3,\"lastLaunchedAt\":10}]");

        assertEquals(2, result.size());
        assertEquals(3, result.get(0).appId);
        assertEquals(1, result.get(1).appId);
    }

    @Test
    public void recentsAreBoundedToTwelve() {
        StringBuilder encoded = new StringBuilder("[");
        for (int index = 0; index < 20; index++) {
            if (index != 0) {
                encoded.append(',');
            }
            encoded.append("{\"appId\":").append(index)
                    .append(",\"lastLaunchedAt\":").append(index).append('}');
        }
        encoded.append(']');

        assertEquals(12, LauncherLibraryStore.decodeRecents(encoded.toString()).size());
    }
}
