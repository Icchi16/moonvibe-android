package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GamepadActionTest {
    @Test
    public void nintendoLabelsSwapFaceButtons() {
        assertEquals("A", GamepadAction.label(GamepadAction.B, true));
        assertEquals("B", GamepadAction.label(GamepadAction.A, true));
        assertEquals("X", GamepadAction.label(GamepadAction.Y, true));
        assertEquals("Y", GamepadAction.label(GamepadAction.X, true));
    }

    @Test
    public void nintendoLabelsKeepOtherButtons() {
        assertEquals("LB", GamepadAction.label(GamepadAction.LB, true));
        assertEquals("RT", GamepadAction.label(GamepadAction.RT, true));
        assertEquals("START", GamepadAction.label(GamepadAction.START, true));
    }

    @Test
    public void xboxLabelsAreUnchanged() {
        for (int action = 0; action < GamepadAction.COUNT; action++) {
            assertEquals(GamepadAction.label(action), GamepadAction.label(action, false));
        }
    }

    @Test
    public void nintendoTwinUndoesItself() {
        for (int action = 0; action < GamepadAction.COUNT; action++) {
            assertEquals(action, GamepadAction.nintendoTwin(GamepadAction.nintendoTwin(action)));
        }
    }
}
