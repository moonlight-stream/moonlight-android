package com.limelight.binding.input.genshin;

import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for GenshinInputHandler (Section 8A):
 * - 8-direction joystick -> WASD + Left Ctrl walk/run + deadzone + full release
 * - 100x symmetric down/up per button with zero drift
 * - Focus loss / disconnect releaseAll() with 5+ simultaneous inputs
 * - Alt + 1..4 exact down/up ordering
 * - Relative camera delta scaling & remainder accumulation
 * - Sprint hold vs toggle and Shift vs RMB
 */
public class GenshinInputHandlerTest {

    private static class Event {
        final String type; // KEY_DOWN, KEY_UP, MOUSE_DOWN, MOUSE_UP, MOUSE_MOVE
        final int code;
        final int extra; // modifier for key, dy for move

        Event(String type, int code, int extra) {
            this.type = type;
            this.code = code;
            this.extra = extra;
        }

        @Override
        public String toString() {
            return type + "(" + code + "," + extra + ")";
        }
    }

    private static class RecordingSink implements GenshinInputHandler.InputSink {
        final List<Event> events = new ArrayList<>();

        @Override
        public void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags) {
            String t = (keyDirection == KeyboardPacket.KEY_DOWN) ? "KEY_DOWN" : "KEY_UP";
            events.add(new Event(t, keyMap, modifier));
        }

        @Override
        public void sendMouseMove(short deltaX, short deltaY) {
            events.add(new Event("MOUSE_MOVE", deltaX, deltaY));
        }

        @Override
        public void sendMouseButtonDown(byte mouseButton) {
            events.add(new Event("MOUSE_DOWN", mouseButton, 0));
        }

        @Override
        public void sendMouseButtonUp(byte mouseButton) {
            events.add(new Event("MOUSE_UP", mouseButton, 0));
        }

        void clear() {
            events.clear();
        }
    }

    private RecordingSink sink;
    private GenshinInputHandler handler;

    @Before
    public void setUp() {
        sink = new RecordingSink();
        handler = new GenshinInputHandler(sink);
    }

    @Test
    public void joystickDeadzoneSendsNoKeys() {
        handler.updateJoystick(0.0f, 0.0f);
        handler.updateJoystick(0.05f, -0.05f);
        handler.updateJoystick(-0.10f, 0.08f);
        assertEquals(0, sink.events.size());
        assertEquals(0, handler.getPressedKeyCount());
    }

    @Test
    public void joystickEightDirectionsAndRelease() {
        // Screen coords: +X right, -Y up, +Y down
        // N -> W
        handler.updateJoystick(0f, -0.9f);
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_W));
        assertFalse(handler.isKeyPressed(GenshinMapping.KEY_A));
        assertFalse(handler.isKeyPressed(GenshinMapping.KEY_S));
        assertFalse(handler.isKeyPressed(GenshinMapping.KEY_D));
        assertFalse(handler.isKeyPressed(GenshinMapping.KEY_LCTRL));

        // NE -> W + D
        handler.updateJoystick(0.7f, -0.7f);
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_W));
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_D));
        assertFalse(handler.isKeyPressed(GenshinMapping.KEY_A));
        assertFalse(handler.isKeyPressed(GenshinMapping.KEY_S));

        // E -> D
        handler.updateJoystick(0.9f, 0f);
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_D));
        assertFalse(handler.isKeyPressed(GenshinMapping.KEY_W));

        // SE -> S + D
        handler.updateJoystick(0.7f, 0.7f);
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_S));
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_D));

        // S -> S
        handler.updateJoystick(0f, 0.9f);
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_S));
        assertFalse(handler.isKeyPressed(GenshinMapping.KEY_D));

        // SW -> S + A
        handler.updateJoystick(-0.7f, 0.7f);
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_S));
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_A));

        // W -> A
        handler.updateJoystick(-0.9f, 0f);
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_A));
        assertFalse(handler.isKeyPressed(GenshinMapping.KEY_S));

        // NW -> W + A
        handler.updateJoystick(-0.7f, -0.7f);
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_W));
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_A));

        // Release -> all WASD released
        handler.releaseJoystick();
        assertEquals(0, handler.getPressedKeyCount());
        assertEquals(0, handler.getActiveModifiers());
    }

    @Test
    public void joystickWalkThresholdHoldsAndReleasesLeftCtrl() {
        // Magnitude 0.35 (< 0.50) -> walk = Left Ctrl + W
        handler.updateJoystick(0f, -0.35f);
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_W));
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_LCTRL));
        assertEquals(KeyboardPacket.MODIFIER_CTRL, handler.getActiveModifiers());

        // Push to magnitude 0.85 (>= 0.50) -> run = W only, Ctrl released
        handler.updateJoystick(0f, -0.85f);
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_W));
        assertFalse(handler.isKeyPressed(GenshinMapping.KEY_LCTRL));
        assertEquals(0, handler.getActiveModifiers());

        // Release joystick -> nothing held
        handler.releaseJoystick();
        assertEquals(0, handler.getPressedKeyCount());
    }

    @Test
    public void everyButton100TimesSymmetricDownUp() {
        for (GenshinMapping.Action action : GenshinMapping.Action.values()) {
            sink.clear();
            if (action.inputType == GenshinMapping.InputType.CHARACTER_SLOT) {
                for (int i = 0; i < 100; i++) {
                    handler.tapCharacterSlot(action.characterSlot);
                }
                assertEquals(200, sink.events.size());
                assertEquals(0, handler.getPressedKeyCount());
            } else {
                for (int i = 0; i < 100; i++) {
                    handler.onActionDown(action);
                    handler.onActionUp(action);
                }
                int downs = 0;
                int ups = 0;
                for (Event e : sink.events) {
                    if (e.type.equals("KEY_DOWN") || e.type.equals("MOUSE_DOWN")) {
                        downs++;
                    } else if (e.type.equals("KEY_UP") || e.type.equals("MOUSE_UP")) {
                        ups++;
                    }
                }
                assertEquals("Down count for " + action, 100, downs);
                assertEquals("Up count for " + action, 100, ups);
                assertEquals("No stuck keys for " + action, 0, handler.getPressedKeyCount());
                assertEquals("No stuck mouse for " + action, 0, handler.getPressedMouseButtonCount());
            }
        }
    }

    @Test
    public void releaseAllClearsFiveSimultaneousInputs() {
        // Simulate 5-finger combat: joystick NW walk + Attack LMB + Sprint Shift + Skill E + Elemental Sight MMB
        handler.updateJoystick(-0.25f, -0.25f); // W + A + LCtrl
        handler.onActionDown(GenshinMapping.Action.ATTACK); // LMB
        handler.onActionDown(GenshinMapping.Action.SPRINT); // LShift
        handler.onActionDown(GenshinMapping.Action.SKILL_E); // E
        handler.onActionDown(GenshinMapping.Action.ELEMENTAL_SIGHT); // MMB

        assertTrue(handler.getPressedKeyCount() >= 5);
        assertEquals(2, handler.getPressedMouseButtonCount());

        // Simulate focus loss / disconnect
        handler.releaseAll();

        assertEquals(0, handler.getPressedKeyCount());
        assertEquals(0, handler.getPressedMouseButtonCount());
        assertEquals(0, handler.getActiveModifiers());
    }

    @Test
    public void altPlusCharSlotExactSequenceForAllFourSlots() {
        short[] expectedKeys = {
                GenshinMapping.KEY_1,
                GenshinMapping.KEY_2,
                GenshinMapping.KEY_3,
                GenshinMapping.KEY_4
        };

        for (int slot = 1; slot <= 4; slot++) {
            sink.clear();
            handler.triggerCharacterBurst(slot);

            assertEquals("Expected 4 events for Alt+" + slot, 4, sink.events.size());

            // 1. Left Alt DOWN with MODIFIER_ALT
            assertEquals("KEY_DOWN", sink.events.get(0).type);
            assertEquals(GenshinMapping.KEY_LALT, sink.events.get(0).code);
            assertEquals(KeyboardPacket.MODIFIER_ALT, sink.events.get(0).extra);

            // 2. Number DOWN with MODIFIER_ALT
            assertEquals("KEY_DOWN", sink.events.get(1).type);
            assertEquals(expectedKeys[slot - 1], sink.events.get(1).code);
            assertEquals(KeyboardPacket.MODIFIER_ALT, sink.events.get(1).extra);

            // 3. Number UP with MODIFIER_ALT
            assertEquals("KEY_UP", sink.events.get(2).type);
            assertEquals(expectedKeys[slot - 1], sink.events.get(2).code);
            assertEquals(KeyboardPacket.MODIFIER_ALT, sink.events.get(2).extra);

            // 4. Left Alt UP with modifier 0
            assertEquals("KEY_UP", sink.events.get(3).type);
            assertEquals(GenshinMapping.KEY_LALT, sink.events.get(3).code);
            assertEquals(0, sink.events.get(3).extra);

            assertEquals(0, handler.getPressedKeyCount());
            assertEquals(0, handler.getActiveModifiers());
        }
    }

    @Test
    public void cameraSensitivityAndSubPixelAccumulation() {
        handler.setCameraSensitivity(0.5f);
        handler.sendCameraDelta(1.0f, 1.0f); // 0.5 -> remainder 0.5, no packet yet
        assertEquals(0, sink.events.size());

        handler.sendCameraDelta(1.0f, 1.0f); // 0.5 + 0.5 = 1.0 -> sends (1, 1)
        assertEquals(1, sink.events.size());
        assertEquals("MOUSE_MOVE", sink.events.get(0).type);
        assertEquals(1, sink.events.get(0).code);
        assertEquals(1, sink.events.get(0).extra);

        sink.clear();
        handler.setCameraSensitivity(2.0f);
        handler.sendCameraDelta(10.0f, -5.0f);
        assertEquals(1, sink.events.size());
        assertEquals(20, sink.events.get(0).code);
        assertEquals(-10, sink.events.get(0).extra);
    }

    @Test
    public void sprintToggleAndRmbModes() {
        // Toggle mode with Shift
        handler.setSprintToggleMode(true);
        handler.onActionDown(GenshinMapping.Action.SPRINT);
        handler.onActionUp(GenshinMapping.Action.SPRINT);
        assertTrue(handler.isSprintToggledOn());
        assertTrue(handler.isKeyPressed(GenshinMapping.KEY_LSHIFT));

        // Second tap toggles off
        handler.onActionDown(GenshinMapping.Action.SPRINT);
        handler.onActionUp(GenshinMapping.Action.SPRINT);
        assertFalse(handler.isSprintToggledOn());
        assertFalse(handler.isKeyPressed(GenshinMapping.KEY_LSHIFT));

        // RMB hold mode
        handler.setSprintToggleMode(false);
        handler.setSprintUseRmb(true);
        handler.onActionDown(GenshinMapping.Action.SPRINT);
        assertTrue(handler.isMouseButtonPressed(MouseButtonPacket.BUTTON_RIGHT));
        handler.onActionUp(GenshinMapping.Action.SPRINT);
        assertFalse(handler.isMouseButtonPressed(MouseButtonPacket.BUTTON_RIGHT));
    }
}
