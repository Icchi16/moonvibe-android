/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.preferences.OscProfiles;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VirtualController {
    public static class ControllerInputContext {
        public short inputMap = 0x0000;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;
    }

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final ControllerHandler controllerHandler;
    private final Context context;
    private final Handler handler;
    private final PreferenceConfiguration prefConfig;
    private TouchHaptics haptics;
    private final GyroCamera gyro;
    /** Whether the controls are on screen, so the gyroscope may run. */
    private boolean shown = false;

    private static final String MOUSE_MODE_PREF = "osc_mouse_mode";

    private boolean isJoystickMode = true;
    private final OscMenu menu;
    private VirtualKeyboard virtualKeyboard = null;

    /** Layout of every element when the editor was opened, restored by Cancel. */
    private final Map<Integer, String> editSnapshot = new HashMap<>();

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    private FrameLayout frame_layout;

    private ControllerMode currentMode = ControllerMode.Active;
    private final ControllerInputContext inputContext = new ControllerInputContext();

    private final List<VirtualControllerElement> elements = new ArrayList<>();

    /** How many on-screen buttons hold each gamepad action down right now. */
    private final int[] actionHoldCounts = new int[GamepadAction.COUNT];
    /** Right stick output added by sources other than the camera zone, such as Aim+Fire. */
    private final Map<Object, float[]> cameraContributions = new HashMap<>();

    public VirtualController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context, PreferenceConfiguration prefConfig) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.prefConfig = prefConfig;
        this.haptics = new TouchHaptics(context, prefConfig.oscButtonHaptics);

        // Allow snap lines to be drawn outside of button bounds
        this.frame_layout.setClipChildren(false);
        this.frame_layout.setClipToPadding(false);

        isJoystickMode = !context.getSharedPreferences(
                VirtualControllerConfigurationLoader.OSC_PREFERENCE, Context.MODE_PRIVATE)
                .getBoolean(MOUSE_MODE_PREF, false);

        virtualKeyboard = new VirtualKeyboard(context, this);
        virtualKeyboard.setVisibility(View.GONE);
        virtualKeyboard.setClickable(true);
        virtualKeyboard.setFocusable(true);

        gyro = new GyroCamera(context, this);
        menu = new OscMenu(context, new MenuActions());
    }

    private class MenuActions implements OscMenu.Actions {
        @Override
        public boolean isMouseMode() {
            return !isJoystickMode;
        }

        @Override
        public void setMouseMode(boolean mouseMode) {
            isJoystickMode = !mouseMode;
            context.getSharedPreferences(VirtualControllerConfigurationLoader.OSC_PREFERENCE, Context.MODE_PRIVATE)
                    .edit().putBoolean(MOUSE_MODE_PREF, mouseMode).apply();
            updateElementVisibility();
        }

        @Override
        public void toggleKeyboard() {
            if (virtualKeyboard.getVisibility() == View.VISIBLE) {
                virtualKeyboard.setVisibility(View.GONE);
            } else {
                virtualKeyboard.setVisibility(View.VISIBLE);
                virtualKeyboard.bringToFront();
                menu.bringToFront();
            }
        }

        @Override
        public void enterEditMode() {
            releaseAllInputs();
            editSnapshot.clear();
            for (VirtualControllerElement element : elements) {
                try {
                    editSnapshot.put(element.elementId, element.getConfiguration().toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            currentMode = ControllerMode.MoveButtons;
            virtualKeyboard.setVisibility(View.GONE);
            updateElementVisibility();
            menu.setEditing(true);
            redrawElements();
        }

        @Override
        public void finishEditMode(boolean save) {
            if (save) {
                VirtualControllerConfigurationLoader.saveProfile(VirtualController.this, context);
            } else {
                for (VirtualControllerElement element : elements) {
                    String configuration = editSnapshot.get(element.elementId);
                    if (configuration == null) {
                        continue;
                    }
                    try {
                        element.loadConfiguration(new JSONObject(configuration));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            editSnapshot.clear();
            currentMode = ControllerMode.Active;
            updateElementVisibility();
            menu.setEditing(false);
            redrawElements();
        }

        @Override
        public void resetLayoutToDefault() {
            // Only the positions on screen change here: Done saves them and Cancel restores the
            // snapshot taken when the editor opened
            for (VirtualControllerElement element : elements) {
                frame_layout.removeView(element);
            }
            elements.clear();
            VirtualControllerConfigurationLoader.createDefaultLayout(VirtualController.this, context);
            updateElementVisibility();
            virtualKeyboard.bringToFront();
            menu.bringToFront();
            redrawElements();
        }

        @Override
        public int getCameraSensitivity() {
            return prefConfig.oscRightStickCameraSensitivity;
        }

        @Override
        public void setCameraSensitivity(int percent) {
            prefConfig.oscRightStickCameraSensitivity = percent;
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putInt(PreferenceConfiguration.OSC_RIGHT_STICK_CAMERA_SENSITIVITY_PREF_STRING, percent)
                    .apply();
        }

        @Override
        public int getOpacity() {
            return prefConfig.oscOpacity;
        }

        @Override
        public void setOpacity(int percent) {
            prefConfig.oscOpacity = percent;
            VirtualController.this.setOpacity(percent);
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putInt(PreferenceConfiguration.OSC_OPACITY_PREF_STRING, percent)
                    .apply();
        }

        @Override
        public String getHapticsLevel() {
            return prefConfig.oscButtonHaptics;
        }

        @Override
        public void setHapticsLevel(String level) {
            prefConfig.oscButtonHaptics = level;
            haptics = new TouchHaptics(context, level);
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(PreferenceConfiguration.OSC_BUTTON_HAPTICS_PREF_STRING, level)
                    .apply();
            haptics.press();
        }

        @Override
        public void releaseAllInputs() {
            VirtualController.this.releaseAllInputs();
        }

        @Override
        public void holdCalibrationStick(boolean rightStick, int percent) {
            short value = (short) (percent * 0x7FFE / 100);
            if (rightStick) {
                inputContext.rightStickX = value;
                inputContext.rightStickY = 0;
            } else {
                inputContext.leftStickX = value;
                inputContext.leftStickY = 0;
            }
            sendControllerInputContext();
        }

        @Override
        public int getStickAntiDeadzone(boolean rightStick) {
            return rightStick ? prefConfig.oscRightStickAntiDeadzone : prefConfig.oscStickAntiDeadzone;
        }

        @Override
        public void setStickAntiDeadzone(boolean rightStick, int percent) {
            if (rightStick) {
                prefConfig.oscRightStickAntiDeadzone = percent;
            } else {
                prefConfig.oscStickAntiDeadzone = percent;
            }
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putInt(rightStick ? PreferenceConfiguration.OSC_RIGHT_STICK_ANTI_DEADZONE_PREF_STRING :
                            PreferenceConfiguration.OSC_STICK_ANTI_DEADZONE_PREF_STRING, percent)
                    .apply();
        }

        @Override
        public void disconnect() {
            if (context instanceof Activity) {
                ((Activity) context).finish();
            }
        }

        @Override
        public boolean hasGyro() {
            return gyro.isAvailable();
        }

        @Override
        public String getGyroMode() {
            return prefConfig.oscGyroMode;
        }

        @Override
        public void setGyroMode(String mode) {
            prefConfig.oscGyroMode = mode;
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(PreferenceConfiguration.OSC_GYRO_MODE_PREF_STRING, mode)
                    .apply();
            updateGyroListening();
        }

        @Override
        public OscProfiles getProfiles() {
            return new OscProfiles(context);
        }

        @Override
        public void reloadProfile() {
            VirtualController.this.reloadProfile();
        }

        @Override
        public void terminate() {
            if (context instanceof Game) {
                ((Game) context).terminateSession();
            } else {
                disconnect();
            }
        }
    }

    /** Load the settings and layout of the profile that was just made active. */
    private void reloadProfile() {
        releaseAllInputs();
        prefConfig.copyOscProfileSettingsFrom(PreferenceConfiguration.readPreferences(context));
        isJoystickMode = !context.getSharedPreferences(
                VirtualControllerConfigurationLoader.OSC_PREFERENCE, Context.MODE_PRIVATE)
                .getBoolean(MOUSE_MODE_PREF, false);
        refreshLayout();
        menu.show();
        updateGyroListening();
    }

    private void updateGyroListening() {
        gyro.setListening(shown && !PreferenceConfiguration.OSC_GYRO_MODE_OFF.equals(prefConfig.oscGyroMode));
    }

    /** Whether gyro movement should turn the camera right now. */
    boolean isGyroEngaged() {
        if (currentMode != ControllerMode.Active || !isJoystickMode) {
            return false;
        }
        if (PreferenceConfiguration.OSC_GYRO_MODE_ALWAYS.equals(prefConfig.oscGyroMode)) {
            return true;
        }
        if (!PreferenceConfiguration.OSC_GYRO_MODE_AIMING.equals(prefConfig.oscGyroMode)) {
            return false;
        }
        // Aiming means a finger on the camera zone or on an Aim+Fire button
        for (VirtualControllerElement element : elements) {
            if (!element.isPressed()) {
                continue;
            }
            if (element.elementId == VirtualControllerElement.EID_RS ||
                    (element instanceof DigitalButton && ((DigitalButton) element).isAimFire())) {
                return true;
            }
        }
        return false;
    }

    /** Let go of everything held on the controls, so the game is not left with a stuck input. */
    private void releaseAllInputs() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        for (VirtualControllerElement element : elements) {
            if (element.isPressed()) {
                element.onElementTouchEvent(cancel);
            }
        }
        cancel.recycle();
    }

    private void redrawElements() {
        frame_layout.invalidate(); // Redraw snap lines if any
        for (VirtualControllerElement element : elements) {
            element.invalidate();
        }
    }

    Handler getHandler() {
        return handler;
    }

    void sendMouseMove(int deltaX, int deltaY) {
        if (context instanceof Game) {
            ((Game) context).sendRelativeMouseMove(deltaX, deltaY);
        }
    }

    public void hide() {
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.INVISIBLE);
        }

        menu.hide();
        virtualKeyboard.setVisibility(View.GONE);
        shown = false;
        updateGyroListening();
    }

    public void show() {
        updateElementVisibility();

        menu.show();
        shown = true;
        updateGyroListening();
    }

    void updateElementVisibility() {
        boolean editing = currentMode != ControllerMode.Active;
        for (VirtualControllerElement element : elements) {
            // The editor shows every control even in mouse mode, and faded when the user hid it
            boolean visible = editing || (isJoystickMode && !element.isHiddenByUser());
            element.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            element.setAlpha(editing && element.isHiddenByUser() ? 0.35f : 1.0f);
        }
    }

    /** Press or release a gamepad button for one on-screen button. */
    void setActionPressed(int action, boolean pressed) {
        if (!GamepadAction.isValid(action)) {
            return;
        }
        if (pressed) {
            actionHoldCounts[action]++;
        } else if (actionHoldCounts[action] > 0) {
            actionHoldCounts[action]--;
        }
        sendControllerInputContext();
    }

    /** Set the right stick output one extra source adds; 0, 0 removes it. */
    void setCameraContribution(Object source, float x, float y) {
        if (x == 0 && y == 0) {
            if (cameraContributions.remove(source) == null) {
                return;
            }
        } else {
            cameraContributions.put(source, new float[]{x, y});
        }
        sendControllerInputContext();
    }

    /** The editor's options for one control: what it sends, and whether it is shown. */
    void showElementOptions(final VirtualControllerElement element) {
        if (element instanceof RingButtons) {
            showRingOptions((RingButtons) element);
            return;
        }
        final List<String> options = new ArrayList<>();
        final DigitalButton button = element instanceof DigitalButton && ((DigitalButton) element).canChangeAction() ?
                (DigitalButton) element : null;
        if (button != null) {
            options.add(context.getString(R.string.osc_element_send, GamepadAction.label(button.getAction())));
            options.add(context.getString(button.isAimFire() ? R.string.osc_element_aim_fire_on :
                    R.string.osc_element_aim_fire_off));
        }
        options.add(context.getString(element.isHiddenByUser() ? R.string.osc_element_show : R.string.osc_element_hide));

        new android.app.AlertDialog.Builder(context)
                .setItems(options.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (button != null && which == 0) {
                            showActionPicker(button);
                        } else if (button != null && which == 1) {
                            button.setAimFire(!button.isAimFire());
                        } else {
                            element.setHiddenByUser(!element.isHiddenByUser());
                            updateElementVisibility();
                        }
                    }
                })
                .show();
    }

    private void showRingOptions(final RingButtons ring) {
        final int count = ring.getSmallCount();
        final String[] options = new String[count + 3];
        options[0] = context.getString(R.string.osc_ring_count, count);
        options[1] = context.getString(R.string.osc_ring_primary, GamepadAction.label(ring.getPrimaryAction()));
        for (int i = 0; i < count; i++) {
            options[i + 2] = context.getString(R.string.osc_ring_small, i + 1, GamepadAction.label(ring.getSmallAction(i)));
        }
        options[count + 2] = context.getString(ring.isHiddenByUser() ? R.string.osc_element_show : R.string.osc_element_hide);

        new android.app.AlertDialog.Builder(context)
                .setItems(options, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            showRingCountPicker(ring);
                        } else if (which == 1) {
                            showActionPicker(ring.getPrimaryAction(), new ActionPicked() {
                                @Override
                                public void onPicked(int action) {
                                    ring.setPrimaryAction(action);
                                }
                            });
                        } else if (which < count + 2) {
                            final int index = which - 2;
                            showActionPicker(ring.getSmallAction(index), new ActionPicked() {
                                @Override
                                public void onPicked(int action) {
                                    ring.setSmallAction(index, action);
                                }
                            });
                        } else {
                            ring.setHiddenByUser(!ring.isHiddenByUser());
                            updateElementVisibility();
                        }
                    }
                })
                .show();
    }

    private void showRingCountPicker(final RingButtons ring) {
        final int choices = RingButtons.MAX_SMALL - RingButtons.MIN_SMALL + 1;
        String[] labels = new String[choices];
        for (int i = 0; i < choices; i++) {
            labels[i] = String.valueOf(RingButtons.MIN_SMALL + i);
        }
        new android.app.AlertDialog.Builder(context)
                .setTitle(R.string.osc_ring_count_title)
                .setSingleChoiceItems(labels, ring.getSmallCount() - RingButtons.MIN_SMALL,
                        new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        ring.setSmallCount(RingButtons.MIN_SMALL + which);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private interface ActionPicked {
        void onPicked(int action);
    }

    private void showActionPicker(final DigitalButton button) {
        showActionPicker(button.getAction(), new ActionPicked() {
            @Override
            public void onPicked(int action) {
                button.setAction(action);
            }
        });
    }

    private void showActionPicker(int current, final ActionPicked callback) {
        new android.app.AlertDialog.Builder(context)
                .setTitle(R.string.osc_element_send_title)
                .setSingleChoiceItems(GamepadAction.pickerLabels(), current,
                        new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        callback.onPicked(which);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void removeElements() {
        for (VirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        menu.detach(frame_layout);
        frame_layout.removeView(virtualKeyboard);
    }

    public void setOpacity(int opacity) {
        for (VirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(VirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<VirtualControllerElement> getElements() {
        return elements;
    }

    public PreferenceConfiguration getPrefConfig() {
        return prefConfig;
    }

    TouchHaptics getHaptics() {
        return haptics;
    }

    /** Elements that move together with this one in the layout editor. */
    List<VirtualControllerElement> getMoveGroup(VirtualControllerElement element) {
        List<VirtualControllerElement> group = new ArrayList<>();
        if (isFaceButton(element)) {
            for (VirtualControllerElement other : elements) {
                if (isFaceButton(other) && other.getVisibility() == View.VISIBLE) {
                    group.add(other);
                }
            }
        }
        if (!group.contains(element)) {
            group.clear();
            group.add(element);
        }
        return group;
    }

    /** Extra reach around single buttons when touch assist is on. */
    private static final float TOUCH_ASSIST_SLOP_DP = 10.0f;

    private static boolean isFaceButton(VirtualControllerElement element) {
        return element.elementId == VirtualControllerElement.EID_A ||
                element.elementId == VirtualControllerElement.EID_B ||
                element.elementId == VirtualControllerElement.EID_X ||
                element.elementId == VirtualControllerElement.EID_Y;
    }

    private static float distanceToCenter(VirtualControllerElement element, float x, float y) {
        float dx = x - (element.getX() + element.getWidth() / 2f);
        float dy = y - (element.getY() + element.getHeight() / 2f);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Finds the button a touch at (x, y) in layout coordinates was most likely aimed at, for a
     * touch that missed every button and would otherwise reach a joystick zone behind them.
     * The A/B/X/Y block is a diamond split into four smaller diamonds, one per face button, each
     * centered on its button and just large enough to hold the button's square. Other buttons
     * reach a little past their edges.
     */
    VirtualControllerElement findTouchAssistTarget(float x, float y) {
        if (prefConfig == null || !prefConfig.oscTouchAssist) {
            return null;
        }

        float slop = TOUCH_ASSIST_SLOP_DP * context.getResources().getDisplayMetrics().density;

        List<VirtualControllerElement> faceButtons = new ArrayList<>();
        float centroidX = 0, centroidY = 0;
        for (VirtualControllerElement element : elements) {
            if (isFaceButton(element) && element.getVisibility() == View.VISIBLE) {
                faceButtons.add(element);
                centroidX += element.getX() + element.getWidth() / 2f;
                centroidY += element.getY() + element.getHeight() / 2f;
            }
        }

        VirtualControllerElement best = null;
        float bestDistance = Float.MAX_VALUE;

        if (faceButtons.size() >= 3) {
            centroidX /= faceButtons.size();
            centroidY /= faceButtons.size();

            for (VirtualControllerElement element : faceButtons) {
                float buttonX = element.getX() + element.getWidth() / 2f;
                float buttonY = element.getY() + element.getHeight() / 2f;
                // Diamond around the button reaching halfway to the block's center on each side
                float halfDiagonal = Math.abs(buttonX - centroidX) + Math.abs(buttonY - centroidY);
                float diamondDistance = Math.abs(x - buttonX) + Math.abs(y - buttonY);
                if (diamondDistance <= halfDiagonal && diamondDistance < bestDistance) {
                    best = element;
                    bestDistance = diamondDistance;
                }
            }
            if (best != null) {
                return best;
            }
        }

        for (VirtualControllerElement element : elements) {
            if (element instanceof AnalogStick || element instanceof RingButtons || isFaceButton(element) ||
                    element.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (x >= element.getX() - slop && x <= element.getX() + element.getWidth() + slop &&
                    y >= element.getY() - slop && y <= element.getY() + element.getHeight() + slop) {
                float distance = distanceToCenter(element, x, y);
                if (distance < bestDistance) {
                    best = element;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        frame_layout.invalidate(); // Request a redraw for snap lines
        removeElements();
        java.util.Arrays.fill(actionHoldCounts, 0);
        cameraContributions.clear();

        if (currentMode != ControllerMode.Active) {
            // A rotation or resize rebuilds everything, so leave the editor without saving
            editSnapshot.clear();
            currentMode = ControllerMode.Active;
            menu.setEditing(false);
        }

        menu.attach(frame_layout);

        FrameLayout.LayoutParams vkParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        vkParams.gravity = android.view.Gravity.BOTTOM;
        frame_layout.addView(virtualKeyboard, vkParams);

        // Start with the default layout
        VirtualControllerConfigurationLoader.createDefaultLayout(this, context);

        // Apply user preferences onto the default layout
        VirtualControllerConfigurationLoader.loadFromPreferences(this, context);

        updateElementVisibility();

        // Keep the keyboard and the menu above the controls
        virtualKeyboard.bringToFront();
        menu.bringToFront();
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    private void sendControllerInputContextInternal() {
        _DBG("INPUT_MAP + " + inputContext.inputMap);
        _DBG("LEFT_TRIGGER " + inputContext.leftTrigger);
        _DBG("RIGHT_TRIGGER " + inputContext.rightTrigger);
        _DBG("LEFT STICK X: " + inputContext.leftStickX + " Y: " + inputContext.leftStickY);
        _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);

        if (controllerHandler != null) {
            // inputMap is a short: without the mask, Y (0x8000) would also set every flag above it
            int buttonFlags = inputContext.inputMap & 0xFFFF;
            short rightStickX = inputContext.rightStickX;
            short rightStickY = inputContext.rightStickY;
            if (!cameraContributions.isEmpty()) {
                float[] mixed = StickMix.combine(inputContext.rightStickX / (float) 0x7FFE,
                        inputContext.rightStickY / (float) 0x7FFE, cameraContributions.values());
                rightStickX = (short) (mixed[0] * 0x7FFE);
                rightStickY = (short) (mixed[1] * 0x7FFE);
            }
            for (int action = 0; action < GamepadAction.COUNT; action++) {
                if (actionHoldCounts[action] > 0) {
                    buttonFlags |= GamepadAction.flag(action);
                }
            }
            controllerHandler.reportOscState(
                    buttonFlags,
                    inputContext.leftStickX,
                    inputContext.leftStickY,
                    rightStickX,
                    rightStickY,
                    actionHoldCounts[GamepadAction.LT] > 0 ? (byte) 0xFF : inputContext.leftTrigger,
                    actionHoldCounts[GamepadAction.RT] > 0 ? (byte) 0xFF : inputContext.rightTrigger
            );
        }
    }

    void sendControllerInputContext() {
        // Cancel retransmissions of prior gamepad inputs
        handler.removeCallbacks(delayedRetransmitRunnable);

        sendControllerInputContextInternal();

        // HACK: GFE sometimes discards gamepad packets when they are received
        // very shortly after another. This can be critical if an axis zeroing packet
        // is lost and causes an analog stick to get stuck. To avoid this, we retransmit
        // the gamepad state a few times unless another input event happens before then.
        handler.postDelayed(delayedRetransmitRunnable, 25);
        handler.postDelayed(delayedRetransmitRunnable, 50);
        handler.postDelayed(delayedRetransmitRunnable, 75);
    }
}
