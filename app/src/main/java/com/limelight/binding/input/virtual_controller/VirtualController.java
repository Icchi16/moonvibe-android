/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

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

    private boolean isJoystickMode = true;
    private ImageButton buttonToggleMode;
    private ImageButton buttonKeyboard;
    private VirtualKeyboard virtualKeyboard = null;

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    private FrameLayout frame_layout;

    private ControllerMode currentMode = ControllerMode.Active;
    private final ControllerInputContext inputContext = new ControllerInputContext();

    private ImageButton buttonConfigure;

    private final List<VirtualControllerElement> elements = new ArrayList<>();

    public VirtualController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context, PreferenceConfiguration prefConfig) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.prefConfig = prefConfig;

        // Allow snap lines to be drawn outside of button bounds
        this.frame_layout.setClipChildren(false);
        this.frame_layout.setClipToPadding(false);

        buttonToggleMode = new ImageButton(context);
        buttonToggleMode.setAlpha(0.6f);
        buttonToggleMode.setFocusable(false);
        buttonToggleMode.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        buttonToggleMode.setScaleType(ImageView.ScaleType.FIT_CENTER);
        buttonToggleMode.setImageResource(R.drawable.ic_gamepad);
        buttonToggleMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isJoystickMode = !isJoystickMode;
                updateElementVisibility();
                buttonToggleMode.setImageResource(isJoystickMode ? R.drawable.ic_gamepad : R.drawable.ic_mouse);
            }
        });

        buttonKeyboard = new ImageButton(context);
        buttonKeyboard.setAlpha(0.6f);
        buttonKeyboard.setFocusable(false);
        buttonKeyboard.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        buttonKeyboard.setScaleType(ImageView.ScaleType.FIT_CENTER);
        buttonKeyboard.setImageResource(R.drawable.ic_keyboard);
        buttonKeyboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (virtualKeyboard.getVisibility() == View.VISIBLE) {
                    virtualKeyboard.setVisibility(View.GONE);
                } else {
                    virtualKeyboard.setVisibility(View.VISIBLE);
                    virtualKeyboard.bringToFront();
                }
            }
        });

        virtualKeyboard = new VirtualKeyboard(context);
        virtualKeyboard.setVisibility(View.GONE);
        virtualKeyboard.setClickable(true);
        virtualKeyboard.setFocusable(true);

        buttonConfigure = new ImageButton(context);
        buttonConfigure.setAlpha(0.5f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        buttonConfigure.setScaleType(ImageView.ScaleType.FIT_CENTER);
        buttonConfigure.setImageResource(R.drawable.ic_settings);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;

                if (currentMode == ControllerMode.Active){
                    currentMode = ControllerMode.MoveButtons;
                    message = "Entering configuration mode (Move buttons)";
                } else if (currentMode == ControllerMode.MoveButtons) {
                    currentMode = ControllerMode.ResizeButtons;
                    message = "Entering configuration mode (Resize buttons)";
                } else {
                    currentMode = ControllerMode.Active;
                    VirtualControllerConfigurationLoader.saveProfile(VirtualController.this, context);
                    message = "Exiting configuration mode";
                }

                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

                buttonConfigure.invalidate();
                frame_layout.invalidate(); // Redraw snap lines if any

                for (VirtualControllerElement element : elements) {
                    if (element instanceof AnalogStick && ((AnalogStick) element).isFloatingMode()) {
                        // For floating sticks, they might be invisible in active mode.
                        // Force a redraw when entering/exiting config mode.
                        element.invalidate();
                    }
                    element.invalidate();
                }
            }
        });

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

        buttonConfigure.setVisibility(View.INVISIBLE);
        buttonToggleMode.setVisibility(View.INVISIBLE);
        buttonKeyboard.setVisibility(View.INVISIBLE);
        virtualKeyboard.setVisibility(View.GONE);
    }

    public void show() {
        updateElementVisibility();

        buttonConfigure.setVisibility(View.VISIBLE);
        buttonToggleMode.setVisibility(View.VISIBLE);
        buttonKeyboard.setVisibility(View.VISIBLE);
    }

    private void updateElementVisibility() {
        for (VirtualControllerElement element : elements) {
            element.setVisibility(isJoystickMode ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void removeElements() {
        for (VirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
        frame_layout.removeView(buttonToggleMode);
        frame_layout.removeView(buttonKeyboard);
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
     * The whole A/B/X/Y block, gaps included, belongs to its nearest face button; other buttons
     * reach a little past their edges.
     */
    VirtualControllerElement findTouchAssistTarget(float x, float y) {
        if (prefConfig == null || !prefConfig.oscTouchAssist) {
            return null;
        }

        float slop = TOUCH_ASSIST_SLOP_DP * context.getResources().getDisplayMetrics().density;

        float faceLeft = Float.MAX_VALUE, faceTop = Float.MAX_VALUE;
        float faceRight = -Float.MAX_VALUE, faceBottom = -Float.MAX_VALUE;
        float faceButtonSize = 0;
        for (VirtualControllerElement element : elements) {
            if (isFaceButton(element) && element.getVisibility() == View.VISIBLE) {
                faceLeft = Math.min(faceLeft, element.getX());
                faceTop = Math.min(faceTop, element.getY());
                faceRight = Math.max(faceRight, element.getX() + element.getWidth());
                faceBottom = Math.max(faceBottom, element.getY() + element.getHeight());
                faceButtonSize = Math.max(faceButtonSize, Math.min(element.getWidth(), element.getHeight()));
            }
        }

        VirtualControllerElement best = null;
        float bestDistance = Float.MAX_VALUE;

        float faceMargin = Math.max(slop, faceButtonSize * 0.25f);
        if (faceButtonSize > 0 &&
                x >= faceLeft - faceMargin && x <= faceRight + faceMargin &&
                y >= faceTop - faceMargin && y <= faceBottom + faceMargin) {
            for (VirtualControllerElement element : elements) {
                if (isFaceButton(element) && element.getVisibility() == View.VISIBLE) {
                    float distance = distanceToCenter(element, x, y);
                    if (distance < bestDistance) {
                        best = element;
                        bestDistance = distance;
                    }
                }
            }
            return best;
        }

        for (VirtualControllerElement element : elements) {
            if (element instanceof AnalogStick || element.getVisibility() != View.VISIBLE) {
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

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        // Use a more substantial button size (about 10% of screen height)
        int buttonSize = (int)(screen.heightPixels * 0.10f);
        int margin = 20;

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.leftMargin = margin;
        params.topMargin = margin;
        frame_layout.addView(buttonConfigure, params);

        FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        toggleParams.leftMargin = margin + buttonSize + margin;
        toggleParams.topMargin = margin;
        frame_layout.addView(buttonToggleMode, toggleParams);

        FrameLayout.LayoutParams kbParams = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        kbParams.leftMargin = margin + buttonSize + margin + buttonSize + margin;
        kbParams.topMargin = margin;
        frame_layout.addView(buttonKeyboard, kbParams);

        FrameLayout.LayoutParams vkParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        vkParams.gravity = android.view.Gravity.BOTTOM;
        frame_layout.addView(virtualKeyboard, vkParams);

        // Ensure buttons are on top
        buttonConfigure.bringToFront();
        buttonToggleMode.bringToFront();
        buttonKeyboard.bringToFront();

        // Start with the default layout
        VirtualControllerConfigurationLoader.createDefaultLayout(this, context);

        // Apply user preferences onto the default layout
        VirtualControllerConfigurationLoader.loadFromPreferences(this, context);

        // Ensure functional buttons and keyboard are ALWAYS on top of joysticks
        buttonConfigure.bringToFront();
        buttonToggleMode.bringToFront();
        buttonKeyboard.bringToFront();
        virtualKeyboard.bringToFront();
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
