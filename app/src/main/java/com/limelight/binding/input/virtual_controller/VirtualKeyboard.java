package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.view.KeyEvent;

import com.limelight.Game;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.input.KeyboardPacket;

public class VirtualKeyboard extends LinearLayout {
    private final Game game;
    private final KeyboardTranslator translator;
    private boolean isAltLayout = false;

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

        if (!isAltLayout) {
            // Main Alphabet Layout
            addRow(new String[]{"TAB", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]"});
            addRow(new String[]{"CAPS", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "ENTER"});
            addRow(new String[]{"SHIFT", "Z", "X", "C", "V", "B", "N", "M", ",", ".", "/", "UP"});
        } else {
            // Numbers and F-Keys Layout
            addRow(new String[]{"ESC", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"});
            addRow(new String[]{"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "BS"});
            addRow(new String[]{"INS", "DEL", "HOME", "END", "PGUP", "PGDN", "PRT", "SLCK", "PAUSE", "UP", "\\"});
        }

        // Fixed bottom row
        addRow(new String[]{isAltLayout ? "ABC" : "123", "CTRL", "WIN", "ALT", "SPACE", "LEFT", "DOWN", "RIGHT", "CLOSE"});
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
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setPadding(0, 5, 0, 5);
        b.setAllCaps(false);
        b.setFocusable(false);
        
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        if (label.equals("SPACE")) lp.weight = 2.5f;
        if (label.equals("ENTER") || label.equals("SHIFT") || label.equals("BS")) lp.weight = 1.5f;
        b.setLayoutParams(lp);
        
        final int androidKeyCode = getKeyCode(label);

        b.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (label.equals("CLOSE")) {
                        VirtualKeyboard.this.setVisibility(GONE);
                        return true;
                    }
                    if (label.equals("123") || label.equals("ABC")) {
                        isAltLayout = !isAltLayout;
                        updateLayout();
                        return true;
                    }
                }

                byte action = -1;
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    action = KeyboardPacket.KEY_DOWN;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    action = KeyboardPacket.KEY_UP;
                }
                
                if (action != -1 && androidKeyCode != 0) {
                    short translated = translator.translate(androidKeyCode, -1);
                    game.sendKeyboardEvent(translated, action, (byte) 0);
                }
                return true;
            }
        });

        return b;
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
