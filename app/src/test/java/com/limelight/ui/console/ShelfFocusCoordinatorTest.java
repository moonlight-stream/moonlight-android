package com.limelight.ui.console;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ShelfFocusCoordinatorTest {
    @Test
    public void projectionClampsToTargetShelf() {
        ShelfFocusCoordinator coordinator = new ShelfFocusCoordinator();
        assertEquals(2, coordinator.project(5, 3));
        assertEquals(0, coordinator.project(0, 3));
        assertEquals(-1, coordinator.project(2, 0));
    }

    @Test
    public void restoreSurvivesRemovalAndEmptyShelves() {
        ShelfFocusCoordinator coordinator = new ShelfFocusCoordinator();
        coordinator.remember("all", 8);
        assertEquals(8, coordinator.restore("all", 10));
        assertEquals(3, coordinator.restore("all", 4));
        assertEquals(-1, coordinator.restore("all", 0));
    }
}
