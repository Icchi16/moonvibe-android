package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.limelight.Game;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class VirtualKeyboard extends LinearLayout {
    private static final long SHIFT_DOUBLE_TAP_TIMEOUT_MS = 400;

    private final Game game;
    private final KeyboardTranslator translator;
    private final Set<Integer> activeModifierKeyCodes = new HashSet<>();
    private final Set<Integer> pressedKeyCodes = new HashSet<>();
    private final List<Button> keyButtons = new ArrayList<>();

    private boolean isAltLayout = false;
    private boolean isShiftLocked = false;
    private long lastShiftTapTime = 0;

    public VirtualKeyboard(Context context) {
        super(context);
        this.game = (Game) context;
        this.translator = game.getKeyboardTranslator();
        
        setOrientation(VERTICAL);
        setBackgroundColor(0xEE111111);
        setGravity(Gravity.CENTER);
        
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setPadding(0, 10, 0, 10);

        updateLayout();

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
    }

    private void updateLayout() {
        removeAllViews();
        keyButtons.clear();

        if (!isAltLayout) {
            // Phone-style alphabet layout with Backspace always within reach.
            addRow(new String[]{"TAB", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "BS"});
            addRow(new String[]{"CAPS", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "ENTER"});
            addRow(new String[]{"SHIFT", "Z", "X", "C", "V", "B", "N", "M", ",", ".", "/", "UP"});
        } else {
            // Numbers, symbols, and desktop function keys.
            addRow(new String[]{"ESC", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"});
            addRow(new String[]{"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "BS"});
            addRow(new String[]{"SHIFT", "INS", "DEL", "HOME", "END", "PGUP", "PGDN", "PRT", "SLCK", "PAUSE", "[", "]", "\\", "UP"});
        }

        // Desktop modifiers and navigation remain available on both layouts.
        addRow(new String[]{isAltLayout ? "ABC" : "123", "CTRL", "WIN", "ALT", "SPACE", "LEFT", "DOWN", "RIGHT", "CLOSE"});
        updateKeyVisualState();
    }

    private void addRow(String[] keys) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        
        for (String key : keys) {
            Button b = createKey(key);
            row.addView(b);
        }
        addView(row);
    }

    private Button createKey(final String label) {
        Button b = new Button(getContext());
        b.setTag(label);
        b.setText(getDisplayLabel(label));
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setPadding(0, 5, 0, 5);
        b.setAllCaps(false);
        b.setFocusable(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setBackgroundTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_activated},
                        new int[]{android.R.attr.state_pressed},
                        new int[]{}
                },
                new int[]{0xFF2877D4, 0xFF666666, 0xFF3A3A3A}
        ));
        
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        if (label.equals("SPACE")) lp.weight = 2.5f;
        if (label.equals("ENTER") || label.equals("SHIFT") || label.equals("BS")) lp.weight = 1.5f;
        b.setLayoutParams(lp);
        
        final int androidKeyCode = getKeyCode(label);

        b.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();

                if (action == MotionEvent.ACTION_UP) {
                    v.performClick();
                    if (label.equals("CLOSE")) {
                        releaseAllInput();
                        VirtualKeyboard.this.setVisibility(GONE);
                        return true;
                    }
                    if (label.equals("123") || label.equals("ABC")) {
                        isAltLayout = !isAltLayout;
                        updateLayout();
                        return true;
                    }
                    if (isModifierKeyCode(androidKeyCode)) {
                        toggleModifier(androidKeyCode, event.getEventTime());
                        return true;
                    }
                }

                if (androidKeyCode == 0 || isModifierKeyCode(androidKeyCode)) {
                    return true;
                }

                if (action == MotionEvent.ACTION_DOWN) {
                    if (pressedKeyCodes.add(androidKeyCode)) {
                        sendAndroidKeyEvent(androidKeyCode, KeyboardPacket.KEY_DOWN);
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (pressedKeyCodes.remove(androidKeyCode)) {
                        sendAndroidKeyEvent(androidKeyCode, KeyboardPacket.KEY_UP);
                        releaseOneShotModifiers();
                    }
                }
                return true;
            }
        });

        keyButtons.add(b);
        return b;
    }

    private String getDisplayLabel(String label) {
        if (label.equals("BS")) {
            return "⌫";
        }

        if (isModifierActive(KeyEvent.KEYCODE_SHIFT_LEFT)) {
            String shiftedLabel = getShiftedLabel(label);
            if (shiftedLabel != null) {
                return shiftedLabel;
            }
        }

        if (label.length() == 1) {
            char c = label.charAt(0);
            if (c >= 'A' && c <= 'Z' && !isModifierActive(KeyEvent.KEYCODE_SHIFT_LEFT)) {
                return label.toLowerCase(Locale.ROOT);
            }
        }
        return label;
    }

    private String getShiftedLabel(String label) {
        switch (label) {
            case "`": return "~";
            case "1": return "!";
            case "2": return "@";
            case "3": return "#";
            case "4": return "$";
            case "5": return "%";
            case "6": return "^";
            case "7": return "&";
            case "8": return "*";
            case "9": return "(";
            case "0": return ")";
            case "-": return "_";
            case "=": return "+";
            case "[": return "{";
            case "]": return "}";
            case "\\": return "|";
            case ";": return ":";
            case "'": return "\"";
            case ",": return "<";
            case ".": return ">";
            case "/": return "?";
            default: return null;
        }
    }

    private void toggleModifier(int keyCode, long eventTime) {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT) {
            if (!isModifierActive(keyCode)) {
                pressModifier(keyCode);
                isShiftLocked = false;
                lastShiftTapTime = eventTime;
            } else if (!isShiftLocked && eventTime - lastShiftTapTime <= SHIFT_DOUBLE_TAP_TIMEOUT_MS) {
                isShiftLocked = true;
                lastShiftTapTime = 0;
            } else {
                releaseModifier(keyCode);
            }
        } else if (isModifierActive(keyCode)) {
            releaseModifier(keyCode);
        } else {
            pressModifier(keyCode);
        }

        updateKeyVisualState();
    }

    private void pressModifier(int keyCode) {
        if (activeModifierKeyCodes.add(keyCode)) {
            sendAndroidKeyEvent(keyCode, KeyboardPacket.KEY_DOWN);
        }
    }

    private void releaseModifier(int keyCode) {
        if (activeModifierKeyCodes.remove(keyCode)) {
            sendAndroidKeyEvent(keyCode, KeyboardPacket.KEY_UP);
        }

        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT) {
            isShiftLocked = false;
            lastShiftTapTime = 0;
        }
    }

    private void releaseOneShotModifiers() {
        releaseModifier(KeyEvent.KEYCODE_META_LEFT);
        releaseModifier(KeyEvent.KEYCODE_ALT_LEFT);
        releaseModifier(KeyEvent.KEYCODE_CTRL_LEFT);
        if (!isShiftLocked) {
            releaseModifier(KeyEvent.KEYCODE_SHIFT_LEFT);
        }
        updateKeyVisualState();
    }

    private void releaseAllInput() {
        for (int keyCode : new HashSet<>(pressedKeyCodes)) {
            sendAndroidKeyEvent(keyCode, KeyboardPacket.KEY_UP);
        }
        pressedKeyCodes.clear();

        releaseModifier(KeyEvent.KEYCODE_META_LEFT);
        releaseModifier(KeyEvent.KEYCODE_ALT_LEFT);
        releaseModifier(KeyEvent.KEYCODE_CTRL_LEFT);
        releaseModifier(KeyEvent.KEYCODE_SHIFT_LEFT);
        updateKeyVisualState();
    }

    private void sendAndroidKeyEvent(int keyCode, byte keyAction) {
        short translated = translator.translate(keyCode, -1);
        if (translated != 0) {
            game.sendKeyboardEvent(translated, keyAction, getActiveModifierMask());
        }
    }

    private byte getActiveModifierMask() {
        byte modifierMask = 0;
        if (isModifierActive(KeyEvent.KEYCODE_SHIFT_LEFT)) {
            modifierMask |= KeyboardPacket.MODIFIER_SHIFT;
        }
        if (isModifierActive(KeyEvent.KEYCODE_CTRL_LEFT)) {
            modifierMask |= KeyboardPacket.MODIFIER_CTRL;
        }
        if (isModifierActive(KeyEvent.KEYCODE_ALT_LEFT)) {
            modifierMask |= KeyboardPacket.MODIFIER_ALT;
        }
        if (isModifierActive(KeyEvent.KEYCODE_META_LEFT)) {
            modifierMask |= KeyboardPacket.MODIFIER_META;
        }
        return modifierMask;
    }

    private boolean isModifierActive(int keyCode) {
        return activeModifierKeyCodes.contains(keyCode);
    }

    private boolean isModifierKeyCode(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
                keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                keyCode == KeyEvent.KEYCODE_META_LEFT;
    }

    private void updateKeyVisualState() {
        for (Button button : keyButtons) {
            String label = (String) button.getTag();
            int keyCode = getKeyCode(label);
            button.setText(getDisplayLabel(label));
            button.setActivated(isModifierKeyCode(keyCode) && isModifierActive(keyCode));
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // View's constructor may invoke this override before our fields are initialized.
        if (visibility != VISIBLE && pressedKeyCodes != null) {
            releaseAllInput();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAllInput();
        super.onDetachedFromWindow();
    }

    private int getKeyCode(String label) {
        switch (label) {
            case "ESC": return KeyEvent.KEYCODE_ESCAPE;
            case "F1": return KeyEvent.KEYCODE_F1;
            case "F2": return KeyEvent.KEYCODE_F2;
            case "F3": return KeyEvent.KEYCODE_F3;
            case "F4": return KeyEvent.KEYCODE_F4;
            case "F5": return KeyEvent.KEYCODE_F5;
            case "F6": return KeyEvent.KEYCODE_F6;
            case "F7": return KeyEvent.KEYCODE_F7;
            case "F8": return KeyEvent.KEYCODE_F8;
            case "F9": return KeyEvent.KEYCODE_F9;
            case "F10": return KeyEvent.KEYCODE_F10;
            case "F11": return KeyEvent.KEYCODE_F11;
            case "F12": return KeyEvent.KEYCODE_F12;
            case "`": return KeyEvent.KEYCODE_GRAVE;
            case "-": return KeyEvent.KEYCODE_MINUS;
            case "=": return KeyEvent.KEYCODE_EQUALS;
            case "BS": return KeyEvent.KEYCODE_DEL;
            case "TAB": return KeyEvent.KEYCODE_TAB;
            case "[": return KeyEvent.KEYCODE_LEFT_BRACKET;
            case "]": return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case "\\": return KeyEvent.KEYCODE_BACKSLASH;
            case "CAPS": return KeyEvent.KEYCODE_CAPS_LOCK;
            case ";": return KeyEvent.KEYCODE_SEMICOLON;
            case "'": return KeyEvent.KEYCODE_APOSTROPHE;
            case "ENTER": return KeyEvent.KEYCODE_ENTER;
            case "SHIFT": return KeyEvent.KEYCODE_SHIFT_LEFT;
            case ",": return KeyEvent.KEYCODE_COMMA;
            case ".": return KeyEvent.KEYCODE_PERIOD;
            case "/": return KeyEvent.KEYCODE_SLASH;
            case "UP": return KeyEvent.KEYCODE_DPAD_UP;
            case "CTRL": return KeyEvent.KEYCODE_CTRL_LEFT;
            case "WIN": return KeyEvent.KEYCODE_META_LEFT;
            case "ALT": return KeyEvent.KEYCODE_ALT_LEFT;
            case "SPACE": return KeyEvent.KEYCODE_SPACE;
            case "LEFT": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "DOWN": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "RIGHT": return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "INS": return KeyEvent.KEYCODE_INSERT;
            case "DEL": return KeyEvent.KEYCODE_FORWARD_DEL;
            case "HOME": return KeyEvent.KEYCODE_MOVE_HOME;
            case "END": return KeyEvent.KEYCODE_MOVE_END;
            case "PGUP": return KeyEvent.KEYCODE_PAGE_UP;
            case "PGDN": return KeyEvent.KEYCODE_PAGE_DOWN;
            case "PRT": return KeyEvent.KEYCODE_SYSRQ;
            case "SLCK": return KeyEvent.KEYCODE_SCROLL_LOCK;
            case "PAUSE": return KeyEvent.KEYCODE_BREAK;
        }
        if (label.length() == 1) {
            char c = label.charAt(0);
            if (c >= 'A' && c <= 'Z') return KeyEvent.KEYCODE_A + (c - 'A');
            if (c >= '0' && c <= '9') return KeyEvent.KEYCODE_0 + (c - '0');
        }
        return 0;
    }
}
