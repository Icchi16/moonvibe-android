package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.Game;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class VirtualKeyboard extends LinearLayout {
    private static final long SHIFT_DOUBLE_TAP_TIMEOUT_MS = 400;
    /** Holding Ctrl, Win or Alt this long locks it down for a one-finger shortcut. */
    private static final long MODIFIER_LATCH_MS = 400;

    private static final int KEY_HEIGHT_DP = 40;
    private static final int SHORTCUT_HEIGHT_DP = 34;
    private static final int POPUP_HEIGHT_DP = 56;
    private static final int POPUP_MIN_WIDTH_DP = 48;

    private static final String SHORTCUT_PREFIX = "SC:";

    private final Game game;
    private final VirtualController controller;
    private final KeyboardTranslator translator;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Modifiers currently down on the host. */
    private final Set<Integer> activeModifierKeyCodes = new HashSet<>();
    /** Modifiers a finger is holding right now. */
    private final Set<Integer> heldModifierKeyCodes = new HashSet<>();
    /** Ctrl, Win or Alt locked by a long press, released after the next key. */
    private final Set<Integer> latchedModifierKeyCodes = new HashSet<>();
    /** Modifiers that were already active when their key went down, so this press releases them. */
    private final Set<Integer> modifiersToReleaseOnUp = new HashSet<>();
    /** Held modifiers that another key was typed with, so lifting them must release them. */
    private final Set<Integer> modifiersUsedWhileHeld = new HashSet<>();
    private final Map<Integer, Runnable> latchRunnables = new HashMap<>();
    private final Set<Integer> pressedKeyCodes = new HashSet<>();
    private final List<Button> keyButtons = new ArrayList<>();

    private boolean isAltLayout = false;
    private boolean isShiftLocked = false;
    private boolean isCapsOn = false;
    private boolean modifierUsed = false;
    private long lastShiftTapTime = 0;

    private TextView keyPopup;
    private View keyPopupOwner;

    public VirtualKeyboard(Context context, VirtualController controller) {
        super(context);
        this.game = (Game) context;
        this.controller = controller;
        this.translator = game.getKeyboardTranslator();

        setOrientation(VERTICAL);
        setBackgroundColor(0xEE111111);
        setGravity(Gravity.CENTER);

        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setPadding(0, dp(4), 0, dp(4));

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
        hideKeyPopup();

        if (controller.getPrefConfig().oscKeyboardShortcuts) {
            addShortcutRow();
        }

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

    /** The shortcuts people need most when driving a PC from a phone. */
    private void addShortcutRow() {
        LinearLayout row = newRow();
        addShortcut(row, "Alt+Tab", KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ALT_LEFT);
        addShortcut(row, "Win+D", KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_META_LEFT);
        addShortcut(row, "Ctrl+C", KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_CTRL_LEFT);
        addShortcut(row, "Ctrl+V", KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_CTRL_LEFT);
        addShortcut(row, "Ctrl+Z", KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_CTRL_LEFT);
        addShortcut(row, "Ctrl+Shift+Esc", KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT);
        addView(row);
    }

    private void addShortcut(LinearLayout row, String label, final int keyCode, final int... modifiers) {
        Button b = newKeyButton(SHORTCUT_PREFIX + label, label, 12, 0xFF2A2A2A);
        LayoutParams lp = new LayoutParams(0, dp(SHORTCUT_HEIGHT_DP), label.length() > 8 ? 1.6f : 1.0f);
        b.setLayoutParams(lp);
        b.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    controller.getHaptics().tick();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    v.performClick();
                    sendShortcut(keyCode, modifiers);
                }
                return true;
            }
        });
        row.addView(b);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addRow(String[] keys) {
        LinearLayout row = newRow();
        for (String key : keys) {
            Button b = createKey(key);
            row.addView(b);
        }
        addView(row);
    }

    private Button newKeyButton(String tag, String text, float textSizeSp, int color) {
        Button b = new Button(getContext());
        b.setTag(tag);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        b.setPadding(0, 0, 0, 0);
        b.setAllCaps(false);
        b.setFocusable(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setSingleLine(true);
        b.setBackgroundTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_activated},
                        new int[]{android.R.attr.state_pressed},
                        new int[]{}
                },
                new int[]{0xFF2877D4, 0xFF666666, color}
        ));
        keyButtons.add(b);
        return b;
    }

    private Button createKey(final String label) {
        Button b = newKeyButton(label, getDisplayLabel(label), label.length() == 1 ? 16 : 12, 0xFF3A3A3A);

        LayoutParams lp = new LayoutParams(0, dp(KEY_HEIGHT_DP), 1.0f);
        if (label.equals("SPACE")) lp.weight = 2.5f;
        if (label.equals("ENTER") || label.equals("SHIFT") || label.equals("BS")) lp.weight = 1.5f;
        b.setLayoutParams(lp);

        final int androidKeyCode = getKeyCode(label);

        b.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();

                if (action == MotionEvent.ACTION_DOWN) {
                    controller.getHaptics().tick();
                }

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
                }

                if (androidKeyCode == 0) {
                    return true;
                }

                if (isModifierKeyCode(androidKeyCode)) {
                    handleModifierTouch(androidKeyCode, action, event.getEventTime());
                    return true;
                }

                if (action == MotionEvent.ACTION_DOWN) {
                    if (label.length() == 1) {
                        showKeyPopup(v);
                    }
                    if (pressedKeyCodes.add(androidKeyCode)) {
                        modifierUsed = true;
                        modifiersUsedWhileHeld.addAll(heldModifierKeyCodes);
                        if (androidKeyCode == KeyEvent.KEYCODE_CAPS_LOCK) {
                            isCapsOn = !isCapsOn;
                            updateKeyVisualState();
                        }
                        sendAndroidKeyEvent(androidKeyCode, KeyboardPacket.KEY_DOWN);
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (keyPopupOwner == v) {
                        hideKeyPopup();
                    }
                    if (pressedKeyCodes.remove(androidKeyCode)) {
                        sendAndroidKeyEvent(androidKeyCode, KeyboardPacket.KEY_UP);
                        releaseOneShotModifiers();
                    }
                }
                return true;
            }
        });

        return b;
    }

    private String getDisplayLabel(String label) {
        if (label.equals("BS")) {
            return "⌫";
        }

        boolean shift = isModifierActive(KeyEvent.KEYCODE_SHIFT_LEFT);
        if (shift) {
            String shiftedLabel = getShiftedLabel(label);
            if (shiftedLabel != null) {
                return shiftedLabel;
            }
        }

        if (label.length() == 1) {
            char c = label.charAt(0);
            // Caps Lock flips letters only, and Shift flips them back
            if (c >= 'A' && c <= 'Z' && shift == isCapsOn) {
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

    /**
     * Ctrl, Win and Alt act like real keys: a tap sends a press and release (so Win alone opens
     * Start), holding one while another finger types makes a shortcut, and a long press locks it
     * down for the next key. Shift stays phone-style: a tap shifts the next key and a double tap
     * locks it, while holding it works like a real Shift.
     */
    private void handleModifierTouch(final int keyCode, int action, long eventTime) {
        if (action == MotionEvent.ACTION_DOWN) {
            heldModifierKeyCodes.add(keyCode);
            if (isModifierActive(keyCode)) {
                modifiersToReleaseOnUp.add(keyCode);
            }

            if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT) {
                modifierUsed = false;
                pressModifier(keyCode);
            } else if (!isModifierActive(keyCode)) {
                pressModifier(keyCode);
                Runnable latch = new Runnable() {
                    @Override
                    public void run() {
                        latchRunnables.remove(keyCode);
                        latchedModifierKeyCodes.add(keyCode);
                        controller.getHaptics().press();
                        updateKeyVisualState();
                    }
                };
                latchRunnables.put(keyCode, latch);
                handler.postDelayed(latch, MODIFIER_LATCH_MS);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            heldModifierKeyCodes.remove(keyCode);
            cancelLatch(keyCode);
            boolean wasActive = modifiersToReleaseOnUp.remove(keyCode);
            boolean usedWhileHeld = modifiersUsedWhileHeld.remove(keyCode);

            if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT) {
                if (modifierUsed && !isShiftLocked) {
                    // Held like a real Shift while typing
                    releaseModifier(keyCode);
                } else if (!wasActive) {
                    // A tap shifts the next key
                    lastShiftTapTime = eventTime;
                } else if (!isShiftLocked && eventTime - lastShiftTapTime <= SHIFT_DOUBLE_TAP_TIMEOUT_MS) {
                    isShiftLocked = true;
                    lastShiftTapTime = 0;
                } else {
                    releaseModifier(keyCode);
                }
            } else if (wasActive || usedWhileHeld || !latchedModifierKeyCodes.contains(keyCode)) {
                // A tap, the end of a held shortcut, or a tap that unlocks a locked key
                latchedModifierKeyCodes.remove(keyCode);
                releaseModifier(keyCode);
            }
        }

        updateKeyVisualState();
    }

    private void cancelLatch(int keyCode) {
        Runnable latch = latchRunnables.remove(keyCode);
        if (latch != null) {
            handler.removeCallbacks(latch);
        }
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

    /** After a key is typed, let go of modifiers that were only waiting for it. */
    private void releaseOneShotModifiers() {
        int[] modifiers = {KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_CTRL_LEFT};
        for (int keyCode : modifiers) {
            if (!heldModifierKeyCodes.contains(keyCode)) {
                latchedModifierKeyCodes.remove(keyCode);
                releaseModifier(keyCode);
            }
        }
        if (!isShiftLocked && !heldModifierKeyCodes.contains(KeyEvent.KEYCODE_SHIFT_LEFT)) {
            releaseModifier(KeyEvent.KEYCODE_SHIFT_LEFT);
        }
        updateKeyVisualState();
    }

    private void releaseAllInput() {
        hideKeyPopup();

        for (int keyCode : new HashSet<>(pressedKeyCodes)) {
            sendAndroidKeyEvent(keyCode, KeyboardPacket.KEY_UP);
        }
        pressedKeyCodes.clear();

        for (int keyCode : new HashSet<>(latchRunnables.keySet())) {
            cancelLatch(keyCode);
        }
        heldModifierKeyCodes.clear();
        modifiersUsedWhileHeld.clear();
        latchedModifierKeyCodes.clear();
        modifiersToReleaseOnUp.clear();

        releaseModifier(KeyEvent.KEYCODE_META_LEFT);
        releaseModifier(KeyEvent.KEYCODE_ALT_LEFT);
        releaseModifier(KeyEvent.KEYCODE_CTRL_LEFT);
        releaseModifier(KeyEvent.KEYCODE_SHIFT_LEFT);
        updateKeyVisualState();
    }

    /** Press the modifiers, tap the key, then release the modifiers in reverse order. */
    private void sendShortcut(int keyCode, int[] modifiers) {
        byte modifierMask = getActiveModifierMask();
        List<Integer> pressedModifiers = new ArrayList<>();
        for (int modifier : modifiers) {
            if (!isModifierActive(modifier)) {
                modifierMask |= getModifierFlag(modifier);
                sendKey(modifier, KeyboardPacket.KEY_DOWN, modifierMask);
                pressedModifiers.add(modifier);
            }
        }
        sendKey(keyCode, KeyboardPacket.KEY_DOWN, modifierMask);
        sendKey(keyCode, KeyboardPacket.KEY_UP, modifierMask);
        for (int i = pressedModifiers.size() - 1; i >= 0; i--) {
            int modifier = pressedModifiers.get(i);
            modifierMask &= ~getModifierFlag(modifier);
            sendKey(modifier, KeyboardPacket.KEY_UP, modifierMask);
        }
    }

    private void sendAndroidKeyEvent(int keyCode, byte keyAction) {
        sendKey(keyCode, keyAction, getActiveModifierMask());
    }

    private void sendKey(int keyCode, byte keyAction, byte modifierMask) {
        short translated = translator.translate(keyCode, -1);
        if (translated != 0) {
            game.sendKeyboardEvent(translated, keyAction, modifierMask);
        }
    }

    private static byte getModifierFlag(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT: return KeyboardPacket.MODIFIER_SHIFT;
            case KeyEvent.KEYCODE_CTRL_LEFT: return KeyboardPacket.MODIFIER_CTRL;
            case KeyEvent.KEYCODE_ALT_LEFT: return KeyboardPacket.MODIFIER_ALT;
            case KeyEvent.KEYCODE_META_LEFT: return KeyboardPacket.MODIFIER_META;
            default: return 0;
        }
    }

    private byte getActiveModifierMask() {
        byte modifierMask = 0;
        for (int keyCode : activeModifierKeyCodes) {
            modifierMask |= getModifierFlag(keyCode);
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
            if (label.startsWith(SHORTCUT_PREFIX)) {
                continue;
            }
            int keyCode = getKeyCode(label);
            button.setText(getDisplayLabel(label));
            button.setActivated((isModifierKeyCode(keyCode) && isModifierActive(keyCode)) ||
                    (keyCode == KeyEvent.KEYCODE_CAPS_LOCK && isCapsOn));
        }
    }

    /**
     * Show the pressed character above the finger, which covers the key itself. One view is
     * reused and moved, so showing it costs nothing per key press.
     */
    private void showKeyPopup(View key) {
        if (!(getParent() instanceof ViewGroup)) {
            return;
        }
        ViewGroup parent = (ViewGroup) getParent();

        int width = Math.max((int) (key.getWidth() * 1.4f), dp(POPUP_MIN_WIDTH_DP));
        int height = dp(POPUP_HEIGHT_DP);

        if (keyPopup == null) {
            keyPopup = new TextView(getContext());
            keyPopup.setTextColor(Color.WHITE);
            keyPopup.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            keyPopup.setGravity(Gravity.CENTER);
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xFF505050);
            background.setCornerRadius(dp(10));
            keyPopup.setBackground(background);
            keyPopup.setElevation(dp(6));
        }
        if (keyPopup.getParent() != parent) {
            if (keyPopup.getParent() instanceof ViewGroup) {
                ((ViewGroup) keyPopup.getParent()).removeView(keyPopup);
            }
            parent.addView(keyPopup, new FrameLayout.LayoutParams(width, height));
        } else if (keyPopup.getLayoutParams().width != width) {
            keyPopup.getLayoutParams().width = width;
            keyPopup.requestLayout();
        }

        int[] keyLocation = new int[2];
        int[] parentLocation = new int[2];
        key.getLocationInWindow(keyLocation);
        parent.getLocationInWindow(parentLocation);
        float x = keyLocation[0] - parentLocation[0] + (key.getWidth() - width) / 2f;
        x = Math.max(0, Math.min(x, parent.getWidth() - width));
        float y = keyLocation[1] - parentLocation[1] - height - dp(4);

        keyPopup.setText(((Button) key).getText());
        keyPopup.setX(x);
        keyPopup.setY(y);
        keyPopup.setVisibility(VISIBLE);
        keyPopup.bringToFront();
        keyPopupOwner = key;
    }

    private void hideKeyPopup() {
        if (keyPopup != null) {
            keyPopup.setVisibility(GONE);
        }
        keyPopupOwner = null;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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
        if (keyPopup != null && keyPopup.getParent() instanceof ViewGroup) {
            ((ViewGroup) keyPopup.getParent()).removeView(keyPopup);
        }
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
