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
import android.widget.Toast;

import com.limelight.R;
import com.limelight.preferences.OscProfileDialogs;
import com.limelight.preferences.OscProfiles;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.List;

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
        /** End the stream and quit the app running on the host. */
        void terminate();
        boolean isNintendoLabels();
        void setNintendoLabels(boolean nintendo);
        boolean isHighContrast();
        void setHighContrast(boolean highContrast);
        boolean hasGyro();
        String getGyroMode();
        void setGyroMode(String mode);
        OscProfiles getProfiles();
        /** Remember that the first-run guide was seen or skipped. */
        void setGuideSeen();
        /** Load the settings and layout of the profile that was just made active. */
        void reloadProfile();
        /** Hold one stick pushed right by this percentage, or let go of it at 0. */
        void holdCalibrationStick(boolean rightStick, int percent);
        int getStickAntiDeadzone(boolean rightStick);
        void setStickAntiDeadzone(boolean rightStick, int percent);
    }

    private static final int BUTTON_SIZE_DP = 48;
    private static final int BUTTON_MARGIN_DP = 8;
    private static final int PANEL_WIDTH_DP = 280;
    private static final int IDLE_FADE_MS = 4000;
    private static final float ACTIVE_ALPHA = 0.9f;
    private static final float IDLE_ALPHA = 0.3f;
    private static final int END_STREAM_CONFIRM_MS = 3000;
    private static final int CALIBRATION_MAX_PERCENT = 50;

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
    private final LinearLayout calibrationCard;
    private final LinearLayout guideCard;

    private static final int[][] GUIDE_STEPS = {
            {R.string.osc_guide_menu_title, R.string.osc_guide_menu_body},
            {R.string.osc_guide_camera_title, R.string.osc_guide_camera_body},
            {R.string.osc_guide_profiles_title, R.string.osc_guide_profiles_body},
            {R.string.osc_guide_edit_title, R.string.osc_guide_edit_body},
    };
    private boolean guiding = false;
    private int guideStep = 0;
    private TextView guideCounter;
    private TextView guideTitle;
    private TextView guideBody;
    private TextView guideNext;

    private boolean calibrating = false;
    private boolean calibratingRightStick = true;
    private int calibrationPercent = 0;
    private TextView calibrationRightChip;
    private TextView calibrationLeftChip;
    private TextView calibrationValue;
    private TextView calibrationHint;
    private SeekBar calibrationSeekBar;

    private TextView profileHeader;
    private LinearLayout profileList;

    private TextView gamepadChip;
    private TextView mouseChip;
    private TextView cameraLabel;
    private SeekBar cameraSeekBar;
    private TextView opacityLabel;
    private SeekBar opacitySeekBar;
    private final TextView[] hapticsChips = new TextView[4];
    private final TextView[] gyroChips = new TextView[3];
    private TextView contrastChip;
    private TextView classicLookChip;
    private TextView xboxLabelsChip;
    private TextView nintendoLabelsChip;
    private View gyroSection;
    /** Disconnect or Terminate after its first tap, waiting for the confirming one. */
    private TextView armedItem;
    private int armedItemTextRes;

    private static final String[] GYRO_MODES = {
            PreferenceConfiguration.OSC_GYRO_MODE_OFF, PreferenceConfiguration.OSC_GYRO_MODE_ALWAYS,
            PreferenceConfiguration.OSC_GYRO_MODE_AIMING
    };

    private static final String[] HAPTICS_LEVELS = {
            TouchHaptics.LEVEL_OFF, TouchHaptics.LEVEL_LIGHT, TouchHaptics.LEVEL_MEDIUM, TouchHaptics.LEVEL_STRONG
    };

    private final Runnable fadeRunnable = new Runnable() {
        @Override
        public void run() {
            menuButton.animate().alpha(IDLE_ALPHA).setDuration(300).start();
        }
    };

    private final Runnable disarmConfirmRunnable = new Runnable() {
        @Override
        public void run() {
            if (armedItem != null) {
                armedItem.setText(armedItemTextRes);
                armedItem = null;
            }
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
                if (guiding) {
                    return;
                }
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
                // While calibrating, stray touches must not end it or reach the game
                if (event.getActionMasked() == MotionEvent.ACTION_UP && !calibrating && !guiding) {
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

        calibrationCard = buildCalibrationCard();
        calibrationCard.setVisibility(View.GONE);

        guideCard = buildGuideCard();
        guideCard.setVisibility(View.GONE);
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

        FrameLayout.LayoutParams calibrationParams = new FrameLayout.LayoutParams(
                dp(360), FrameLayout.LayoutParams.WRAP_CONTENT);
        calibrationParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        calibrationParams.topMargin = dp(BUTTON_MARGIN_DP);
        layout.addView(calibrationCard, calibrationParams);

        FrameLayout.LayoutParams guideParams = new FrameLayout.LayoutParams(
                dp(360), FrameLayout.LayoutParams.WRAP_CONTENT);
        guideParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        guideParams.topMargin = dp(BUTTON_MARGIN_DP * 2 + BUTTON_SIZE_DP);
        layout.addView(guideCard, guideParams);

        scheduleFade();
    }

    void detach(FrameLayout layout) {
        handler.removeCallbacks(fadeRunnable);
        handler.removeCallbacks(disarmConfirmRunnable);
        layout.removeView(menuButton);
        layout.removeView(scrim);
        layout.removeView(panel);
        layout.removeView(editToolbar);
        layout.removeView(calibrationCard);
        layout.removeView(guideCard);
    }

    void bringToFront() {
        menuButton.bringToFront();
        scrim.bringToFront();
        panel.bringToFront();
        editToolbar.bringToFront();
        calibrationCard.bringToFront();
        guideCard.bringToFront();
    }

    void show() {
        menuButton.setVisibility(View.VISIBLE);
        wakeButton();
    }

    void hide() {
        finishGuide(false);
        finishCalibration(false);
        closePanel();
        menuButton.setVisibility(View.INVISIBLE);
    }

    void setEditing(boolean editing) {
        finishGuide(false);
        finishCalibration(false);
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
        profileList.setVisibility(View.GONE);
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
        handler.removeCallbacks(disarmConfirmRunnable);
        disarmConfirmRunnable.run();
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
        OscProfiles profiles = actions.getProfiles();
        boolean listOpen = profileList.getVisibility() == View.VISIBLE;
        profileHeader.setText(context.getString(R.string.osc_menu_profile, profiles.getActiveName()) +
                (listOpen ? "  \u25B4" : "  \u25BE"));
        if (listOpen) {
            rebuildProfileList(profiles);
        }

        boolean mouseMode = actions.isMouseMode();
        setChipSelected(gamepadChip, !mouseMode);
        setChipSelected(mouseChip, mouseMode);

        int sensitivity = actions.getCameraSensitivity();
        cameraSeekBar.setProgress((sensitivity - CAMERA_SENSITIVITY_MIN) / CAMERA_SENSITIVITY_STEP);
        cameraLabel.setText(context.getString(R.string.osc_menu_camera_sensitivity, sensitivity));

        int opacity = actions.getOpacity();
        opacitySeekBar.setProgress(opacity);
        opacityLabel.setText(context.getString(R.string.osc_menu_opacity, opacity));

        boolean highContrast = actions.isHighContrast();
        setChipSelected(contrastChip, highContrast);
        setChipSelected(classicLookChip, !highContrast);
        boolean nintendo = actions.isNintendoLabels();
        setChipSelected(xboxLabelsChip, !nintendo);
        setChipSelected(nintendoLabelsChip, nintendo);

        gyroSection.setVisibility(actions.hasGyro() ? View.VISIBLE : View.GONE);
        String gyroMode = actions.getGyroMode();
        for (int i = 0; i < GYRO_MODES.length; i++) {
            setChipSelected(gyroChips[i], GYRO_MODES[i].equals(gyroMode));
        }

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

        profileHeader = itemText(R.string.osc_menu_profile_none);
        profileHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        profileHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                profileList.setVisibility(profileList.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                refreshPanel();
            }
        });
        content.addView(profileHeader);
        profileList = new LinearLayout(context);
        profileList.setOrientation(LinearLayout.VERTICAL);
        profileList.setVisibility(View.GONE);
        content.addView(profileList);
        content.addView(divider());

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
        content.addView(item(R.drawable.ic_gamepad, R.string.osc_menu_calibrate, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCalibration();
            }
        }));
        content.addView(item(R.drawable.ic_settings, R.string.osc_menu_guide, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGuide();
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

        TextView lookLabel = label();
        lookLabel.setText(R.string.osc_menu_look);
        content.addView(lookLabel);
        LinearLayout lookRow = chipRow();
        contrastChip = chip(R.string.osc_look_contrast);
        classicLookChip = chip(R.string.osc_look_classic);
        contrastChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.setHighContrast(true);
                refreshPanel();
            }
        });
        classicLookChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.setHighContrast(false);
                refreshPanel();
            }
        });
        addChip(lookRow, contrastChip);
        addChip(lookRow, classicLookChip);
        content.addView(lookRow);

        TextView labelsLabel = label();
        labelsLabel.setText(R.string.osc_menu_labels);
        content.addView(labelsLabel);
        LinearLayout labelsRow = chipRow();
        xboxLabelsChip = chip(R.string.osc_labels_xbox);
        nintendoLabelsChip = chip(R.string.osc_labels_nintendo);
        xboxLabelsChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.setNintendoLabels(false);
                refreshPanel();
            }
        });
        nintendoLabelsChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.setNintendoLabels(true);
                refreshPanel();
            }
        });
        addChip(labelsRow, xboxLabelsChip);
        addChip(labelsRow, nintendoLabelsChip);
        content.addView(labelsRow);

        LinearLayout gyroBlock = new LinearLayout(context);
        gyroBlock.setOrientation(LinearLayout.VERTICAL);
        TextView gyroLabel = label();
        gyroLabel.setText(R.string.title_osc_gyro_mode);
        gyroBlock.addView(gyroLabel);
        LinearLayout gyroRow = chipRow();
        int[] gyroNames = {R.string.osc_gyro_chip_off, R.string.osc_gyro_chip_always, R.string.osc_gyro_chip_aiming};
        for (int i = 0; i < gyroChips.length; i++) {
            final String mode = GYRO_MODES[i];
            gyroChips[i] = chip(gyroNames[i]);
            gyroChips[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    actions.setGyroMode(mode);
                    refreshPanel();
                }
            });
            addChip(gyroRow, gyroChips[i]);
        }
        gyroBlock.addView(gyroRow);
        gyroSection = gyroBlock;
        content.addView(gyroBlock);

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
        content.addView(confirmedItem(R.string.osc_menu_disconnect, R.string.osc_menu_disconnect_confirm,
                new Runnable() {
                    @Override
                    public void run() {
                        actions.disconnect();
                    }
                }));
        content.addView(confirmedItem(R.string.osc_menu_terminate, R.string.osc_menu_terminate_confirm,
                new Runnable() {
                    @Override
                    public void run() {
                        actions.terminate();
                    }
                }));

        return content;
    }

    /** A red menu item that only acts on a second tap, so a stray touch never ends the stream. */
    private TextView confirmedItem(final int textRes, final int confirmTextRes, final Runnable action) {
        final TextView item = itemText(textRes);
        item.setTextColor(DANGER_TEXT_COLOR);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (armedItem != item) {
                    handler.removeCallbacks(disarmConfirmRunnable);
                    disarmConfirmRunnable.run();
                    armedItem = item;
                    armedItemTextRes = textRes;
                    item.setText(confirmTextRes);
                    handler.postDelayed(disarmConfirmRunnable, END_STREAM_CONFIRM_MS);
                    return;
                }
                closePanel();
                action.run();
            }
        });
        return item;
    }

    private void rebuildProfileList(final OscProfiles profiles) {
        profileList.removeAllViews();
        String activeId = profiles.getActiveId();
        List<OscProfiles.Profile> all = profiles.getProfiles();
        for (final OscProfiles.Profile profile : all) {
            final boolean active = profile.id.equals(activeId);
            TextView row = itemText(R.string.osc_menu_profile_none);
            row.setText(profile.name);
            row.setSingleLine(true);
            row.setMinHeight(dp(44));
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setBackground(roundRect(active ? CHIP_SELECTED_COLOR : CHIP_COLOR, dp(10)));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (active) {
                        return;
                    }
                    profiles.switchTo(profile.id);
                    onActiveProfileChanged(profiles);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    OscProfileDialogs.showProfileOptions(context, profiles, profile,
                            new OscProfileDialogs.ChangeCallback() {
                                @Override
                                public void onChanged() {
                                    refreshPanel();
                                }
                            },
                            new OscProfileDialogs.ChangeCallback() {
                                @Override
                                public void onChanged() {
                                    onActiveProfileChanged(profiles);
                                }
                            });
                    return true;
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(6);
            profileList.addView(row, params);
        }

        TextView newProfile = itemText(R.string.osc_menu_profile_new);
        newProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OscProfileDialogs.showCreateDialog(context, profiles,
                        new OscProfileDialogs.ChangeCallback() {
                            @Override
                            public void onChanged() {
                                refreshPanel();
                            }
                        },
                        new OscProfileDialogs.ChangeCallback() {
                            @Override
                            public void onChanged() {
                                onActiveProfileChanged(profiles);
                            }
                        });
            }
        });
        profileList.addView(newProfile);

        TextView hint = label();
        hint.setText(R.string.osc_menu_profile_hint);
        hint.setTextColor(SECONDARY_TEXT_COLOR);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setPadding(dp(4), 0, dp(4), dp(4));
        profileList.addView(hint);
    }

    private void onActiveProfileChanged(OscProfiles profiles) {
        closePanel();
        actions.reloadProfile();
        Toast.makeText(context, context.getString(R.string.osc_menu_profile, profiles.getActiveName()),
                Toast.LENGTH_SHORT).show();
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

    /**
     * Find the game's stick deadzone: the app holds a stick slightly pushed and the user raises
     * it until the game just starts to react. That value becomes the stick's anti-deadzone, so
     * the smallest swipe starts right where the game begins to respond.
     */
    /** Short cards that point out the menu, camera, profiles and layout editor. */
    void startGuide() {
        if (calibrating || editToolbar.getVisibility() == View.VISIBLE) {
            return;
        }
        closePanel();
        guiding = true;
        guideStep = 0;
        scrim.setVisibility(View.VISIBLE);
        guideCard.setVisibility(View.VISIBLE);
        bringToFront();
        // The first card is about the menu button, so keep it visible above the scrim
        menuButton.bringToFront();
        handler.removeCallbacks(fadeRunnable);
        menuButton.animate().cancel();
        menuButton.setAlpha(ACTIVE_ALPHA);
        showGuideStep();
    }

    private void showGuideStep() {
        guideCounter.setText(context.getString(R.string.osc_guide_counter, guideStep + 1, GUIDE_STEPS.length));
        guideTitle.setText(GUIDE_STEPS[guideStep][0]);
        guideBody.setText(GUIDE_STEPS[guideStep][1]);
        guideNext.setText(guideStep == GUIDE_STEPS.length - 1 ? R.string.osc_guide_done : R.string.osc_guide_next);
    }

    private void finishGuide(boolean markSeen) {
        if (!guiding) {
            return;
        }
        guiding = false;
        guideCard.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
        if (markSeen) {
            actions.setGuideSeen();
        }
        wakeButton();
    }

    private LinearLayout buildGuideCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(PANEL_COLOR, dp(16)));
        int padding = dp(16);
        card.setPadding(padding, padding, padding, dp(12));

        guideCounter = label();
        guideCounter.setTextColor(SECONDARY_TEXT_COLOR);
        guideCounter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        guideCounter.setPadding(0, 0, 0, dp(4));
        card.addView(guideCounter);

        guideTitle = label();
        guideTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        guideTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        guideTitle.setPadding(0, 0, 0, dp(6));
        card.addView(guideTitle);

        guideBody = label();
        guideBody.setTextColor(SECONDARY_TEXT_COLOR);
        guideBody.setPadding(0, 0, 0, dp(12));
        card.addView(guideBody);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        TextView skip = toolbarButton(R.string.osc_guide_skip, CHIP_COLOR);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishGuide(true);
            }
        });
        guideNext = toolbarButton(R.string.osc_guide_next, CHIP_SELECTED_COLOR);
        guideNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (guideStep < GUIDE_STEPS.length - 1) {
                    guideStep++;
                    showGuideStep();
                } else {
                    finishGuide(true);
                }
            }
        });
        buttons.addView(skip);
        buttons.addView(guideNext);
        card.addView(buttons);
        return card;
    }

    private void startCalibration() {
        closePanel();
        calibrating = true;
        scrim.setVisibility(View.VISIBLE);
        calibrationCard.setVisibility(View.VISIBLE);
        bringToFront();
        menuButton.setVisibility(View.INVISIBLE);
        selectCalibrationStick(true);
    }

    private void selectCalibrationStick(boolean rightStick) {
        if (calibrationPercent != 0) {
            actions.holdCalibrationStick(calibratingRightStick, 0);
        }
        calibratingRightStick = rightStick;
        setChipSelected(calibrationRightChip, rightStick);
        setChipSelected(calibrationLeftChip, !rightStick);
        calibrationHint.setText(rightStick ? R.string.osc_calibrate_hint_camera : R.string.osc_calibrate_hint_move);
        setCalibrationPercent(Math.min(actions.getStickAntiDeadzone(rightStick), CALIBRATION_MAX_PERCENT));
    }

    private void setCalibrationPercent(int percent) {
        calibrationPercent = Math.max(0, Math.min(percent, CALIBRATION_MAX_PERCENT));
        calibrationValue.setText(context.getString(R.string.osc_calibrate_value, calibrationPercent));
        if (calibrationSeekBar.getProgress() != calibrationPercent) {
            calibrationSeekBar.setProgress(calibrationPercent);
        }
        actions.holdCalibrationStick(calibratingRightStick, calibrationPercent);
    }

    private void finishCalibration(boolean save) {
        if (!calibrating) {
            return;
        }
        calibrating = false;
        actions.holdCalibrationStick(calibratingRightStick, 0);
        if (save) {
            actions.setStickAntiDeadzone(calibratingRightStick, calibrationPercent);
        }
        calibrationPercent = 0;
        calibrationCard.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
        menuButton.setVisibility(View.VISIBLE);
        wakeButton();
    }

    private LinearLayout buildCalibrationCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(PANEL_COLOR, dp(16)));
        int padding = dp(12);
        card.setPadding(padding, padding, padding, padding);

        TextView title = label();
        title.setText(R.string.osc_menu_calibrate);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setPadding(dp(4), 0, dp(4), dp(4));
        card.addView(title);

        LinearLayout stickRow = chipRow();
        calibrationRightChip = chip(R.string.osc_calibrate_camera);
        calibrationLeftChip = chip(R.string.osc_calibrate_move);
        calibrationRightChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectCalibrationStick(true);
            }
        });
        calibrationLeftChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectCalibrationStick(false);
            }
        });
        addChip(stickRow, calibrationRightChip);
        addChip(stickRow, calibrationLeftChip);
        card.addView(stickRow);

        calibrationHint = new TextView(context);
        calibrationHint.setTextColor(SECONDARY_TEXT_COLOR);
        calibrationHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        calibrationHint.setPadding(dp(4), dp(6), dp(4), dp(6));
        card.addView(calibrationHint);

        calibrationValue = new TextView(context);
        calibrationValue.setTextColor(TEXT_COLOR);
        calibrationValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        calibrationValue.setGravity(Gravity.CENTER);
        card.addView(calibrationValue);

        LinearLayout sliderRow = new LinearLayout(context);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = toolbarButton(R.string.osc_calibrate_minus, CHIP_COLOR);
        minus.setMinWidth(dp(48));
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCalibrationPercent(calibrationPercent - 1);
            }
        });
        calibrationSeekBar = new SeekBar(context);
        calibrationSeekBar.setMax(CALIBRATION_MAX_PERCENT);
        calibrationSeekBar.setOnSeekBarChangeListener(new SeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    setCalibrationPercent(progress);
                }
            }
        });
        TextView plus = toolbarButton(R.string.osc_calibrate_plus, CHIP_COLOR);
        plus.setMinWidth(dp(48));
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCalibrationPercent(calibrationPercent + 1);
            }
        });
        sliderRow.addView(minus);
        sliderRow.addView(calibrationSeekBar, new LinearLayout.LayoutParams(0, dp(44), 1));
        sliderRow.addView(plus);
        card.addView(sliderRow);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(8), 0, 0);
        TextView cancel = toolbarButton(R.string.osc_edit_cancel, CHIP_COLOR);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishCalibration(false);
            }
        });
        TextView save = toolbarButton(R.string.osc_calibrate_save, CHIP_SELECTED_COLOR);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishCalibration(true);
            }
        });
        buttons.addView(cancel);
        buttons.addView(save);
        card.addView(buttons);

        return card;
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
