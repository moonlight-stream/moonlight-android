package com.limelight.binding.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Tests device-specific rear-button matching.
 */
public class RearButtonProfileTest {
    /**
     * Verifies independent ordered slot matching across primary and auxiliary devices.
     */
    @Test
    public void findsIndependentSlotsAcrossInputDevices() {
        RearButtonProfile profile = new RearButtonProfile(
                "primary",
                "Primary controller",
                Arrays.asList(
                        new RearButtonProfile.ButtonBinding("primary", "Primary", 100, 700),
                        new RearButtonProfile.ButtonBinding("aux", "Auxiliary", 101, 701),
                        new RearButtonProfile.ButtonBinding("aux", "Auxiliary", 102, 702),
                        new RearButtonProfile.ButtonBinding("primary", "Primary", 103, 703)));

        assertEquals(1, profile.findSlot("primary", 100, 700));
        assertEquals(2, profile.findSlot("aux", 101, 701));
        assertEquals(3, profile.findSlot("aux", 102, 702));
        assertEquals(4, profile.findSlot("primary", 103, 703));
        assertEquals(0, profile.findSlot("aux", 103, 703));
    }

    /**
     * Verifies that both key code and scan code are part of the event identity.
     */
    @Test
    public void requiresExactKeyAndScanCodes() {
        RearButtonProfile profile = new RearButtonProfile(
                "primary",
                "Primary controller",
                Collections.singletonList(
                        new RearButtonProfile.ButtonBinding("source", "Source", 100, 700)));

        assertEquals(0, profile.findSlot("source", 101, 700));
        assertEquals(0, profile.findSlot("source", 100, 701));
    }

    /**
     * Verifies profile cardinality validation.
     */
    @Test
    public void rejectsEmptyAndOversizedProfiles() {
        assertThrows(IllegalArgumentException.class, () ->
                new RearButtonProfile("primary", "Primary", Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () ->
                new RearButtonProfile(
                        "primary",
                        "Primary",
                        Arrays.asList(
                                binding(1),
                                binding(2),
                                binding(3),
                                binding(4),
                                binding(5))));
    }

    /**
     * Verifies that profiles support every selectable rear-button count.
     */
    @Test
    public void acceptsEverySelectableButtonCount() {
        for (int count = 1; count <= 4; count++) {
            RearButtonProfile.ButtonBinding[] bindings =
                    new RearButtonProfile.ButtonBinding[count];
            for (int i = 0; i < count; i++) {
                bindings[i] = binding(i + 1);
            }

            RearButtonProfile profile = new RearButtonProfile(
                    "primary", "Primary", Arrays.asList(bindings));

            assertEquals(count, profile.getBindings().size());
            assertEquals(count, profile.findSlot("source", count, count));
        }
    }

    /**
     * Builds a unique test binding.
     *
     * @param value key and scan code seed
     * @return test binding
     */
    private static RearButtonProfile.ButtonBinding binding(int value) {
        return new RearButtonProfile.ButtonBinding("source", "Source", value, value);
    }
}
