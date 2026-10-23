/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 */
public class DigitalButton extends VirtualControllerElement {

    /**
     * Listener interface to update registered observers.
     */
    public interface DigitalButtonListener {

        /**
         * onClick event will be fired on button click.
         */
        void onClick();

        /**
         * onLongClick event will be fired on button long click.
         */
        void onLongClick();

        /**
         * onRelease event will be fired on button unpress.
         */
        void onRelease();
    }

    private List<DigitalButtonListener> listeners = new ArrayList<>();
    private String text = "";
    private int icon = -1;
    private long timerLongClickTimeout = 3000;
    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            onLongClickCallback();
        }
    };

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();

    private int layer;
    private DigitalButton movingButton = null;

    /** Gamepad button sent by this button, and the one it sends in the default layout. */
    private int defaultAction = -1;
    private int action = -1;
    private boolean actionDown = false;

    /** Aim+Fire: after pressing, dragging the same finger turns the camera. */
    private boolean aimFire = false;
    private CameraDrag cameraDrag;
    private boolean aiming = false;
    private float aimDownX;
    private float aimDownY;

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public boolean checkMovement(float x, float y, DigitalButton movingButton) {
        // check if the movement happened in the same layer
        if (movingButton.layer != this.layer) {
            return false;
        }

        // save current pressed state
        boolean wasPressed = isPressed();

        // check if the movement directly happened on the button
        if ((this.movingButton == null || movingButton == this.movingButton)
                && this.inRange(x, y)) {
            // set button pressed state depending on moving button pressed state
            if (this.isPressed() != movingButton.isPressed()) {
                this.setPressed(movingButton.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton == this.movingButton) {
            this.setPressed(false);
        }

        // check if a change occurred
        if (wasPressed != isPressed()) {
            if (isPressed()) {
                // is pressed set moving button and emit click event
                this.movingButton = movingButton;
                virtualController.getHaptics().tick();
                onClickCallback();
            } else {
                // no longer pressed reset moving button and emit release event
                this.movingButton = null;
                onReleaseCallback();
            }

            invalidate();

            return true;
        }

        return false;
    }

    private void checkMovementForAllButtons(float x, float y) {
        for (VirtualControllerElement element : virtualController.getElements()) {
            if (element != this && element instanceof DigitalButton) {
                ((DigitalButton) element).checkMovement(x, y, this);
            }
        }
    }

    public DigitalButton(VirtualController controller, int elementId, int layer, Context context) {
        super(controller, context, elementId);
        this.layer = layer;
    }

    public void addDigitalButtonListener(DigitalButtonListener listener) {
        listeners.add(listener);
    }

    void setDefaultAction(int action) {
        this.defaultAction = action;
        this.action = action;
        invalidate();
    }

    int getAction() {
        return action;
    }

    boolean canChangeAction() {
        return GamepadAction.isValid(defaultAction);
    }

    void setAction(int action) {
        if (!GamepadAction.isValid(action) || !canChangeAction()) {
            return;
        }
        if (actionDown) {
            virtualController.setActionPressed(this.action, false);
            actionDown = false;
        }
        this.action = action;
        invalidate();
    }

    boolean isAimFire() {
        return aimFire;
    }

    void setAimFire(boolean aimFire) {
        if (!aimFire && cameraDrag != null) {
            cameraDrag.cancel();
        }
        this.aimFire = aimFire && canChangeAction();
        invalidate();
    }

    private CameraDrag getCameraDrag() {
        if (cameraDrag == null) {
            cameraDrag = new CameraDrag(virtualController, getResources().getDisplayMetrics().density);
        }
        return cameraDrag;
    }

    @Override
    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = super.getConfiguration();
        if (canChangeAction() && action != defaultAction) {
            configuration.put("ACTION", action);
        }
        if (aimFire) {
            configuration.put("AIM", true);
        }
        return configuration;
    }

    @Override
    public void loadConfiguration(JSONObject configuration) throws JSONException {
        super.loadConfiguration(configuration);
        if (canChangeAction()) {
            int savedAction = configuration.optInt("ACTION", defaultAction);
            action = GamepadAction.isValid(savedAction) ? savedAction : defaultAction;
            setAimFire(configuration.optBoolean("AIM", false));
            invalidate();
        }
    }

    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    public void setIcon(int id) {
        this.icon = id;
        invalidate();
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getWidth(), 25));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        paint.setColor(isPressed() ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);

        rect.left = rect.top = paint.getStrokeWidth();
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;

        canvas.drawOval(rect, paint);

        if (icon != -1) {
            Drawable d = getResources().getDrawable(icon);
            d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
            d.draw(canvas);
        } else {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth()/2);
            // A remapped button shows the button it sends
            String label = action != defaultAction ? GamepadAction.label(action) : text;
            canvas.drawText(label, getPercent(getWidth(), 50), getPercent(getHeight(), 63), paint);
        }

        if (aimFire) {
            // A small dot in the top right marks a button that also aims
            paint.setStyle(Paint.Style.FILL);
            float dotRadius = getPercent(Math.min(getWidth(), getHeight()), 7);
            canvas.drawCircle(getWidth() - dotRadius * 2.2f, dotRadius * 2.2f, dotRadius, paint);
        }
    }

    private void onClickCallback() {
        _DBG("clicked");
        if (GamepadAction.isValid(action) && !actionDown) {
            actionDown = true;
            virtualController.setActionPressed(action, true);
        }
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onClick();
        }

        virtualController.getHandler().removeCallbacks(longClickRunnable);
        virtualController.getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void onLongClickCallback() {
        _DBG("long click");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onLongClick();
        }
    }

    private void onReleaseCallback() {
        _DBG("released");
        if (actionDown) {
            actionDown = false;
            virtualController.setActionPressed(action, false);
        }
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onRelease();
        }

        // We may be called for a release without a prior click
        virtualController.getHandler().removeCallbacks(longClickRunnable);
    }

    /** Aim+Fire touch handling: the button stays held while the finger turns the camera. */
    private boolean onAimFireTouchEvent(MotionEvent event) {
        float x = getX() + event.getX();
        float y = getY() + event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                movingButton = null;
                aiming = false;
                aimDownX = x;
                aimDownY = y;
                setPressed(true);
                virtualController.getHaptics().press();
                onClickCallback();
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!aiming) {
                    float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (Math.hypot(x - aimDownX, y - aimDownY) < slop) {
                        return true;
                    }
                    // Start turning from here, so the slop itself causes no jump
                    aiming = true;
                    getCameraDrag().start(x, y);
                    return true;
                }
                getCameraDrag().addMovement(event, getX(), getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (aiming) {
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        getCameraDrag().addMovement(event, getX(), getY());
                        getCameraDrag().stop();
                    } else {
                        getCameraDrag().cancel();
                    }
                }
                aiming = false;
                setPressed(false);
                onReleaseCallback();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        if (aimFire) {
            return onAimFireTouchEvent(event);
        }

        // get masked (not specific to a pointer) action
        float x = getX() + event.getX();
        float y = getY() + event.getY();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                movingButton = null;
                setPressed(true);
                virtualController.getHaptics().press();
                onClickCallback();

                invalidate();

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                checkMovementForAllButtons(x, y);

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                setPressed(false);
                onReleaseCallback();

                checkMovementForAllButtons(x, y);

                invalidate();

                return true;
            }
            default: {
            }
        }
        return true;
    }
}
