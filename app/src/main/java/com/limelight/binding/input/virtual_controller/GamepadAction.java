package com.limelight.binding.input.virtual_controller;

import com.limelight.nvstream.input.ControllerPacket;

/** The gamepad buttons an on-screen button can send. */
final class GamepadAction {
    static final int A = 0;
    static final int B = 1;
    static final int X = 2;
    static final int Y = 3;
    static final int LB = 4;
    static final int RB = 5;
    static final int LT = 6;
    static final int RT = 7;
    static final int L3 = 8;
    static final int R3 = 9;
    static final int START = 10;
    static final int BACK = 11;
    static final int GUIDE = 12;
    static final int UP = 13;
    static final int DOWN = 14;
    static final int LEFT = 15;
    static final int RIGHT = 16;

    static final int COUNT = 17;

    private static final String[] LABELS = {
            "A", "B", "X", "Y", "LB", "RB", "LT", "RT", "L3", "R3",
            "START", "BACK", "GUIDE", "↑", "↓", "←", "→"
    };

    /** Names for the picker, where arrows alone would be unclear. */
    private static final String[] PICKER_LABELS = {
            "A", "B", "X", "Y", "LB", "RB", "LT", "RT", "L3", "R3",
            "Start", "Back", "Guide", "D-pad ↑", "D-pad ↓", "D-pad ←", "D-pad →"
    };

    private static final int[] FLAGS = {
            ControllerPacket.A_FLAG, ControllerPacket.B_FLAG, ControllerPacket.X_FLAG, ControllerPacket.Y_FLAG,
            ControllerPacket.LB_FLAG, ControllerPacket.RB_FLAG, 0, 0,
            ControllerPacket.LS_CLK_FLAG, ControllerPacket.RS_CLK_FLAG,
            ControllerPacket.PLAY_FLAG, ControllerPacket.BACK_FLAG, ControllerPacket.SPECIAL_BUTTON_FLAG,
            ControllerPacket.UP_FLAG, ControllerPacket.DOWN_FLAG, ControllerPacket.LEFT_FLAG, ControllerPacket.RIGHT_FLAG
    };

    private GamepadAction() {
    }

    static boolean isValid(int action) {
        return action >= 0 && action < COUNT;
    }

    static String label(int action) {
        return isValid(action) ? LABELS[action] : "?";
    }

    static String[] pickerLabels() {
        return PICKER_LABELS.clone();
    }

    /** The action that sets this button flag, or -1. */
    static int forFlag(int flag) {
        for (int action = 0; action < COUNT; action++) {
            if (FLAGS[action] == flag && flag != 0) {
                return action;
            }
        }
        return -1;
    }

    /** The button flag this action sets, or 0 for the triggers. */
    static int flag(int action) {
        return isValid(action) ? FLAGS[action] : 0;
    }
}
