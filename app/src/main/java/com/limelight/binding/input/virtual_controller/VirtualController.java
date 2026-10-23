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

        virtualKeyboard = new VirtualKeyboard(context);
        virtualKeyboard.setVisibility(View.GONE);
        virtualKeyboard.setClickable(true);
        virtualKeyboard.setFocusable(true);

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
        public void disconnect() {
            if (context instanceof Activity) {
                ((Activity) context).finish();
            }
        }
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
    }

    public void show() {
        updateElementVisibility();

        menu.show();
    }

    private void updateElementVisibility() {
        for (VirtualControllerElement element : elements) {
            // The editor shows every control even in mouse mode
            element.setVisibility(isJoystickMode || currentMode != ControllerMode.Active ?
                    View.VISIBLE : View.INVISIBLE);
        }
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
            if (element instanceof AnalogStick || isFaceButton(element) ||
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
            controllerHandler.reportOscState(
                    inputContext.inputMap,
                    inputContext.leftStickX,
                    inputContext.leftStickY,
                    inputContext.rightStickX,
                    inputContext.rightStickY,
                    inputContext.leftTrigger,
                    inputContext.rightTrigger
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
