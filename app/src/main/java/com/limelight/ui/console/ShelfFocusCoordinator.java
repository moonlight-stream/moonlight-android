package com.limelight.ui.console;

import java.util.HashMap;
import java.util.Map;

public final class ShelfFocusCoordinator {
    private final Map<String, Integer> focusedIndices = new HashMap<>();

    public void remember(String shelfKey, int index) {
        if (shelfKey != null && index >= 0) {
            focusedIndices.put(shelfKey, index);
        }
    }

    public int restore(String shelfKey, int itemCount) {
        if (itemCount <= 0) {
            return -1;
        }
        int remembered = focusedIndices.containsKey(shelfKey) ?
                focusedIndices.get(shelfKey) : 0;
        return Math.max(0, Math.min(remembered, itemCount - 1));
    }

    public int project(int sourceIndex, int targetItemCount) {
        if (targetItemCount <= 0) {
            return -1;
        }
        return Math.max(0, Math.min(sourceIndex, targetItemCount - 1));
    }

    public void clear(String shelfKey) {
        focusedIndices.remove(shelfKey);
    }
}
