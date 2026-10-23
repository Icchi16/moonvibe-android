package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.limelight.R;

/**
 * The single menu button on top of the on-screen controls, the panel it opens, and the toolbar
 * shown while editing the layout.
 */
class OscMenu {
    interface Actions {
        boolean isMouseMode();
        void setMouseMode(boolean mouseMode);
        void toggleKeyboard();
        void enterEditMode();
        void finishEditMode(boolean save);
        void resetLayoutToDefault();
        int getCameraSensitivity();
        void setCameraSensitivity(int percent);
        int getOpacity();
        void setOpacity(int percent);
        String getHapticsLevel();
        void setHapticsLevel(String level);
        void releaseAllInputs();
        void disconnect();
    }

    private static final int BUTTON_SIZE_DP = 48;
    private static final int BUTTON_MARGIN_DP = 8;
    private static final int PANEL_WIDTH_DP = 280;
    private static final int IDLE_FADE_MS = 4000;
    private static final float ACTIVE_ALPHA = 0.9f;
    private static final float IDLE_ALPHA = 0.3f;
    private static final int DISCONNECT_CONFIRM_MS = 3000;

    private static final int CAMERA_SENSITIVITY_MIN = 25;
    private static final int CAMERA_SENSITIVITY_MAX = 400;
    private static final int CAMERA_SENSITIVITY_STEP = 5;

    private static final int PANEL_COLOR = 0xF0202124;
    private static final int CHIP_COLOR = 0x26FFFFFF;
    private static final int CHIP_SELECTED_COLOR = 0xFF3B6FE0;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SECONDARY_TEXT_COLOR = 0xB3FFFFFF;
    private static final int DANGER_TEXT_COLOR = 0xFFFF8A80;

    private final Context context;
    private final Actions actions;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ImageView menuButton;
    private final View scrim;
    private final ScrollView panel;
    private final LinearLayout editToolbar;

    private TextView gamepadChip;
    private TextView mouseChip;
    private TextView cameraLabel;
    private SeekBar cameraSeekBar;
    private TextView opacityLabel;
    private SeekBar opacitySeekBar;
    private final TextView[] hapticsChips = new TextView[4];
    private TextView disconnectItem;
    private boolean disconnectArmed = false;

    private static final String[] HAPTICS_LEVELS = {
            TouchHaptics.LEVEL_OFF, TouchHaptics.LEVEL_LIGHT, TouchHaptics.LEVEL_MEDIUM, TouchHaptics.LEVEL_STRONG
    };

    private final Runnable fadeRunnable = new Runnable() {
        @Override
        public void run() {
            menuButton.animate().alpha(IDLE_ALPHA).setDuration(300).start();
        }
    };

    private final Runnable disarmDisconnectRunnable = new Runnable() {
        @Override
        public void run() {
            disconnectArmed = false;
            disconnectItem.setText(R.string.osc_menu_disconnect);
        }
    };

    OscMenu(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;

        menuButton = new ImageView(context);
        menuButton.setImageResource(R.drawable.ic_osc_menu);
        menuButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int iconPadding = dp(12);
        menuButton.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
        menuButton.setBackground(roundRect(0x99000000, dp(BUTTON_SIZE_DP) / 2f));
        menuButton.setContentDescription(context.getString(R.string.osc_menu_description));
        menuButton.setFocusable(false);
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (panel.getVisibility() == View.VISIBLE) {
                    closePanel();
                } else {
                    openPanel();
                }
            }
        });

        // Catches taps outside the open panel so they close it instead of reaching the game
        scrim = new View(context);
        scrim.setBackgroundColor(0x33000000);
        scrim.setVisibility(View.GONE);
        scrim.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    closePanel();
                }
                return true;
            }
        });

        panel = new ScrollView(context);
        panel.setBackground(roundRect(PANEL_COLOR, dp(16)));
        panel.setVisibility(View.GONE);
        panel.addView(buildPanelContent());

        editToolbar = buildEditToolbar();
        editToolbar.setVisibility(View.GONE);
    }

    void attach(FrameLayout layout) {
        int buttonSize = dp(BUTTON_SIZE_DP);
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        buttonParams.leftMargin = dp(BUTTON_MARGIN_DP);
        buttonParams.topMargin = dp(BUTTON_MARGIN_DP);
        layout.addView(menuButton, buttonParams);

        layout.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Next to the button rather than under it, which leaves more height on a landscape phone
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                dp(PANEL_WIDTH_DP), FrameLayout.LayoutParams.WRAP_CONTENT);
        panelParams.leftMargin = dp(BUTTON_MARGIN_DP * 2 + BUTTON_SIZE_DP);
        panelParams.topMargin = dp(BUTTON_MARGIN_DP);
        panelParams.bottomMargin = dp(BUTTON_MARGIN_DP);
        layout.addView(panel, panelParams);

        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        toolbarParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        toolbarParams.topMargin = dp(BUTTON_MARGIN_DP);
        layout.addView(editToolbar, toolbarParams);

        scheduleFade();
    }

    void detach(FrameLayout layout) {
        handler.removeCallbacks(fadeRunnable);
        handler.removeCallbacks(disarmDisconnectRunnable);
        layout.removeView(menuButton);
        layout.removeView(scrim);
        layout.removeView(panel);
        layout.removeView(editToolbar);
    }

    void bringToFront() {
        menuButton.bringToFront();
        scrim.bringToFront();
        panel.bringToFront();
        editToolbar.bringToFront();
    }

    void show() {
        menuButton.setVisibility(View.VISIBLE);
        wakeButton();
    }

    void hide() {
        closePanel();
        menuButton.setVisibility(View.INVISIBLE);
    }

    void setEditing(boolean editing) {
        closePanel();
        editToolbar.setVisibility(editing ? View.VISIBLE : View.GONE);
        menuButton.setVisibility(editing ? View.INVISIBLE : View.VISIBLE);
        if (editing) {
            editToolbar.bringToFront();
        } else {
            wakeButton();
        }
    }

    private void openPanel() {
        actions.releaseAllInputs();
        refreshPanel();
        scrim.setVisibility(View.VISIBLE);
        panel.setVisibility(View.VISIBLE);
        panel.scrollTo(0, 0);
        bringToFront();
        handler.removeCallbacks(fadeRunnable);
        menuButton.animate().cancel();
        menuButton.setAlpha(ACTIVE_ALPHA);
    }

    private void closePanel() {
        if (panel.getVisibility() != View.VISIBLE) {
            return;
        }
        panel.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
        handler.removeCallbacks(disarmDisconnectRunnable);
        disarmDisconnectRunnable.run();
        scheduleFade();
    }

    private void wakeButton() {
        menuButton.animate().cancel();
        menuButton.setAlpha(ACTIVE_ALPHA);
        scheduleFade();
    }

    private void scheduleFade() {
        handler.removeCallbacks(fadeRunnable);
        handler.postDelayed(fadeRunnable, IDLE_FADE_MS);
    }

    private void refreshPanel() {
        boolean mouseMode = actions.isMouseMode();
        setChipSelected(gamepadChip, !mouseMode);
        setChipSelected(mouseChip, mouseMode);

        int sensitivity = actions.getCameraSensitivity();
        cameraSeekBar.setProgress((sensitivity - CAMERA_SENSITIVITY_MIN) / CAMERA_SENSITIVITY_STEP);
        cameraLabel.setText(context.getString(R.string.osc_menu_camera_sensitivity, sensitivity));

        int opacity = actions.getOpacity();
        opacitySeekBar.setProgress(opacity);
        opacityLabel.setText(context.getString(R.string.osc_menu_opacity, opacity));

        String haptics = actions.getHapticsLevel();
        for (int i = 0; i < HAPTICS_LEVELS.length; i++) {
            setChipSelected(hapticsChips[i], HAPTICS_LEVELS[i].equals(haptics));
        }
    }

    private View buildPanelContent() {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        content.setPadding(padding, padding, padding, padding);

        LinearLayout modeRow = chipRow();
        gamepadChip = chip(R.string.osc_menu_gamepad);
        mouseChip = chip(R.string.osc_menu_mouse);
        gamepadChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.setMouseMode(false);
                refreshPanel();
            }
        });
        mouseChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.setMouseMode(true);
                refreshPanel();
            }
        });
        addChip(modeRow, gamepadChip);
        addChip(modeRow, mouseChip);
        content.addView(modeRow);

        content.addView(item(R.drawable.ic_keyboard, R.string.osc_menu_keyboard, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePanel();
                actions.toggleKeyboard();
            }
        }));
        content.addView(item(R.drawable.ic_settings, R.string.osc_menu_edit_layout, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.enterEditMode();
            }
        }));

        content.addView(divider());
        content.addView(sectionLabel(R.string.osc_menu_quick_settings));

        cameraLabel = label();
        content.addView(cameraLabel);
        cameraSeekBar = new SeekBar(context);
        cameraSeekBar.setMax((CAMERA_SENSITIVITY_MAX - CAMERA_SENSITIVITY_MIN) / CAMERA_SENSITIVITY_STEP);
        cameraSeekBar.setOnSeekBarChangeListener(new SeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int sensitivity = CAMERA_SENSITIVITY_MIN + progress * CAMERA_SENSITIVITY_STEP;
                cameraLabel.setText(context.getString(R.string.osc_menu_camera_sensitivity, sensitivity));
                actions.setCameraSensitivity(sensitivity);
            }
        });
        content.addView(cameraSeekBar, seekBarParams());

        opacityLabel = label();
        content.addView(opacityLabel);
        opacitySeekBar = new SeekBar(context);
        opacitySeekBar.setMax(100);
        opacitySeekBar.setOnSeekBarChangeListener(new SeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                opacityLabel.setText(context.getString(R.string.osc_menu_opacity, progress));
                actions.setOpacity(progress);
            }
        });
        content.addView(opacitySeekBar, seekBarParams());

        TextView hapticsLabel = label();
        hapticsLabel.setText(R.string.title_osc_button_haptics);
        content.addView(hapticsLabel);
        LinearLayout hapticsRow = chipRow();
        int[] hapticsNames = {
                R.string.osc_button_haptics_off, R.string.osc_button_haptics_light,
                R.string.osc_button_haptics_medium, R.string.osc_button_haptics_strong
        };
        for (int i = 0; i < hapticsChips.length; i++) {
            final String level = HAPTICS_LEVELS[i];
            hapticsChips[i] = chip(hapticsNames[i]);
            hapticsChips[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    actions.setHapticsLevel(level);
                    refreshPanel();
                }
            });
            addChip(hapticsRow, hapticsChips[i]);
        }
        content.addView(hapticsRow);

        content.addView(divider());
        disconnectItem = itemText(R.string.osc_menu_disconnect);
        disconnectItem.setTextColor(DANGER_TEXT_COLOR);
        disconnectItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Ask for a second tap so a stray touch never ends the stream
                if (!disconnectArmed) {
                    disconnectArmed = true;
                    disconnectItem.setText(R.string.osc_menu_disconnect_confirm);
                    handler.postDelayed(disarmDisconnectRunnable, DISCONNECT_CONFIRM_MS);
                    return;
                }
                closePanel();
                actions.disconnect();
            }
        });
        content.addView(disconnectItem);

        return content;
    }

    private LinearLayout buildEditToolbar() {
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setGravity(Gravity.CENTER_HORIZONTAL);
        toolbar.setBackground(roundRect(PANEL_COLOR, dp(16)));
        int padding = dp(8);
        toolbar.setPadding(padding, padding, padding, padding);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        TextView reset = toolbarButton(R.string.osc_edit_default, CHIP_COLOR);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.resetLayoutToDefault();
            }
        });
        TextView cancel = toolbarButton(R.string.osc_edit_cancel, CHIP_COLOR);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.finishEditMode(false);
            }
        });
        TextView done = toolbarButton(R.string.osc_edit_done, CHIP_SELECTED_COLOR);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.finishEditMode(true);
            }
        });
        buttons.addView(reset);
        buttons.addView(cancel);
        buttons.addView(done);
        toolbar.addView(buttons);

        TextView hint = new TextView(context);
        hint.setText(R.string.osc_edit_hint);
        hint.setTextColor(SECONDARY_TEXT_COLOR);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setGravity(Gravity.CENTER);
        hint.setMaxWidth(dp(420));
        hint.setPadding(dp(4), dp(6), dp(4), 0);
        toolbar.addView(hint);

        return toolbar;
    }

    private TextView toolbarButton(int text, int color) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextColor(TEXT_COLOR);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(dp(88));
        button.setMinHeight(dp(44));
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(roundRect(color, dp(12)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout chipRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        return row;
    }

    private TextView chip(int text) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextColor(TEXT_COLOR);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        setChipSelected(chip, false);
        return chip;
    }

    private void addChip(LinearLayout row, TextView chip) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1);
        if (row.getChildCount() > 0) {
            params.leftMargin = dp(6);
        }
        row.addView(chip, params);
    }

    private void setChipSelected(TextView chip, boolean selected) {
        chip.setBackground(roundRect(selected ? CHIP_SELECTED_COLOR : CHIP_COLOR, dp(10)));
    }

    private View item(int icon, int text, View.OnClickListener listener) {
        TextView item = itemText(text);
        android.graphics.drawable.Drawable drawable = context.getResources().getDrawable(icon);
        int iconSize = dp(20);
        drawable.setBounds(0, 0, iconSize, iconSize);
        item.setCompoundDrawablesRelative(drawable, null, null, null);
        item.setCompoundDrawablePadding(dp(12));
        item.setOnClickListener(listener);
        return item;
    }

    private TextView itemText(int text) {
        TextView item = new TextView(context);
        item.setText(text);
        item.setTextColor(TEXT_COLOR);
        item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinHeight(dp(48));
        item.setPadding(dp(4), 0, dp(4), 0);
        return item;
    }

    private TextView sectionLabel(int text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(SECONDARY_TEXT_COLOR);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setAllCaps(true);
        label.setPadding(dp(4), dp(8), dp(4), dp(4));
        return label;
    }

    private TextView label() {
        TextView label = new TextView(context);
        label.setTextColor(TEXT_COLOR);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setPadding(dp(4), dp(8), dp(4), 0);
        return label;
    }

    private LinearLayout.LayoutParams seekBarParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
    }

    private View divider() {
        View divider = new View(context);
        divider.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
        params.topMargin = dp(8);
        params.bottomMargin = dp(4);
        divider.setLayoutParams(params);
        return divider;
    }

    private GradientDrawable roundRect(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private abstract static class SeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
