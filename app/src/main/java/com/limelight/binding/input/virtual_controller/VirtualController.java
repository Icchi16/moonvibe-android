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
    private ImageButton buttonToggleMode = null;
    private ImageButton buttonKeyboard = null;
    private VirtualKeyboard virtualKeyboard = null;

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    private FrameLayout frame_layout = null;

    private ControllerMode currentMode = ControllerMode.Active;
    private final ControllerInputContext inputContext = new ControllerInputContext();

    private ImageButton buttonConfigure = null;

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
