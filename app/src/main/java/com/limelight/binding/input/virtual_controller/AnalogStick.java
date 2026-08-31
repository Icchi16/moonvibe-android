/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.MotionEvent;

import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a analog stick on screen element. It is used to get 2-Axis user input.
 */
public class AnalogStick extends VirtualControllerElement {

    /**
     * outer radius size in percent of the ui element
     */
    public static final int SIZE_RADIUS_COMPLETE = 90;
    /**
     * analog stick size in percent of the ui element
     */
    public static final int SIZE_RADIUS_ANALOG_STICK = 90;
    /**
     * dead zone size in percent of the ui element
     */
    public static final int SIZE_RADIUS_DEADZONE = 90;
    /**
     * time frame for a double click
     */
    public final static long timeoutDoubleClick = 350;

    /**
     * touch down time until the deadzone is lifted to allow precise movements with the analog sticks
     */
    public final static long timeoutDeadzone = 150;

    /** Finger speed that produces full stick deflection at 100% camera sensitivity. */
    private static final float CAMERA_FULL_STICK_SPEED_DP_PER_SECOND = 1000.0f;
    private static final float CAMERA_DEFAULT_FRAME_SECONDS = 1.0f / 60.0f;
    /** Spread touch delta across a few frames to bridge irregular MotionEvent delivery. */
    private static final float CAMERA_DELIVERY_HORIZON_SECONDS = 0.035f;
    private static final float CAMERA_MAX_PENDING_IMPULSE_SECONDS = 0.08f;
    private static final float CAMERA_NOISE_FLOOR = 0.01f;

    private boolean floatingMode = true;
    private float summonedCenterX = -1;
    private float summonedCenterY = -1;

    private float cameraLastTouchX;
    private float cameraLastTouchY;
    private float cameraPendingDeltaX;
    private float cameraPendingDeltaY;
    private float cameraPendingImpulseX;
    private float cameraPendingImpulseY;
    private long cameraLastFrameTimeNanos;
    private float cameraOutputX;
    private float cameraOutputY;
    private boolean cameraUpdateRunnablePosted;
    private long cameraNextUpdateTimeMs;

    private final Runnable cameraUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            cameraUpdateRunnablePosted = false;

            if (!isPressed() || !isRightStickCameraMode()) {
                return;
            }

            updateCameraFrame(SystemClock.uptimeMillis() * 1_000_000L);
            postCameraUpdateRunnable();
        }
    };

    /**
     * Listener interface to update registered observers.
     */
    public interface AnalogStickListener {

        /**
         * onMovement event will be fired on real analog stick movement (outside of the deadzone).
         *
         * @param x horizontal position, value from -1.0 ... 0 .. 1.0
         * @param y vertical position, value from -1.0 ... 0 .. 1.0
         */
        void onMovement(float x, float y);

        /**
         * onClick event will be fired on click on the analog stick
         */
        void onClick();

        /**
         * onDoubleClick event will be fired on a double click in a short time frame on the analog
         * stick.
         */
        void onDoubleClick();

        /**
         * onRevoke event will be fired on unpress of the analog stick.
         */
        void onRevoke();
    }

    /**
     * Movement states of the analog sick.
     */
    private enum STICK_STATE {
        NO_MOVEMENT,
        MOVED_IN_DEAD_ZONE,
        MOVED_ACTIVE
    }

    /**
     * Click type states.
     */
    private enum CLICK_STATE {
        SINGLE,
        DOUBLE
    }

    /**
     * configuration if the analog stick should be displayed as circle or square
     */
    private boolean circle_stick = true; // TODO: implement square sick for simulations

    /**
     * outer radius, this size will be automatically updated on resize
     */
    private float radius_complete = 0;
    /**
     * analog stick radius, this size will be automatically updated on resize
     */
    private float radius_analog_stick = 0;
    /**
     * dead zone radius, this size will be automatically updated on resize
     */
    private float radius_dead_zone = 0;

    /**
     * horizontal position in relation to the center of the element
     */
    private float relative_x = 0;
    /**
     * vertical position in relation to the center of the element
     */
    private float relative_y = 0;


    private double movement_radius = 0;
    private double movement_angle = 0;

    private float position_stick_x = 0;
    private float position_stick_y = 0;

    private final Paint paint = new Paint();

    private STICK_STATE stick_state = STICK_STATE.NO_MOVEMENT;
    private CLICK_STATE click_state = CLICK_STATE.SINGLE;

    private List<AnalogStickListener> listeners = new ArrayList<>();
    private long timeLastClick = 0;

    private static double getMovementRadius(float x, float y) {
        return Math.sqrt(x * x + y * y);
    }

    private static double getAngle(float way_x, float way_y) {
        // prevent divisions by zero for corner cases
        if (way_x == 0) {
            return way_y < 0 ? 0 : Math.PI;
        } else if (way_y == 0) {
            if (way_x > 0) {
                return Math.PI * 3 / 2;
            } else if (way_x < 0) {
                return Math.PI * 1 / 2;
            }
        }
        // return correct calculated angle for each quadrant
        if (way_x > 0) {
            if (way_y < 0) {
                // first quadrant
                return 3 * Math.PI / 2 + Math.atan((double) (-way_y / way_x));
            } else {
                // second quadrant
                return Math.PI + Math.atan((double) (way_x / way_y));
            }
        } else {
            if (way_y > 0) {
                // third quadrant
                return Math.PI / 2 + Math.atan((double) (way_y / -way_x));
            } else {
                // fourth quadrant
                return 0 + Math.atan((double) (-way_x / -way_y));
            }
        }
    }

    public AnalogStick(VirtualController controller, Context context, int elementId) {
        super(controller, context, elementId);
        // reset stick position
        position_stick_x = getWidth() / 2;
        position_stick_y = getHeight() / 2;

        if (controller.getPrefConfig() != null) {
            this.floatingMode = controller.getPrefConfig().oscFloatingJoystick;
        }
    }

    public boolean isFloatingMode() {
        return floatingMode;
    }

    public void addAnalogStickListener(AnalogStickListener listener) {
        listeners.add(listener);
    }

    private void notifyOnMovement(float x, float y) {
        _DBG("movement x: " + x + " movement y: " + y);
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onMovement(x, y);
        }
    }

    private void notifyOnClick() {
        _DBG("click");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onClick();
        }
    }

    private void notifyOnDoubleClick() {
        _DBG("double click");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onDoubleClick();
        }
    }

    private void notifyOnRevoke() {
        _DBG("revoke");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onRevoke();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        PreferenceConfiguration config = virtualController.getPrefConfig();

        // D-pad is 30 units in a 72 unit height grid. Radius is 15 units.
        float dpadRadius = (dm.heightPixels / 72f) * 15f;

        if (floatingMode) {
            // Use preference for floating joystick size if available, otherwise default to D-pad size
            if (config != null) {
                radius_complete = (dm.heightPixels / 72f) * (config.oscFloatingJoystickSize / 2f);
            } else {
                radius_complete = dpadRadius;
            }
        } else {
            // In fixed mode, use the element size.
            radius_complete = getCorrectWidth() * 0.45f;
        }

        // Refined inner circles per user request:
        // Center deadzone (very small)
        radius_dead_zone = radius_complete * 0.05f;
        // Tracking stick (the blue circle) - updated to 25%
        radius_analog_stick = radius_complete * 0.25f;

        super.onSizeChanged(w, h, oldw, oldh);
    }

    private float getCenterX() {
        return (floatingMode && summonedCenterX != -1) ? summonedCenterX : getWidth() / 2.0f;
    }

    private float getCenterY() {
        return (floatingMode && summonedCenterY != -1) ? summonedCenterY : getHeight() / 2.0f;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        boolean isConfigMode = virtualController.getControllerMode() != VirtualController.ControllerMode.Active;

        // In floating mode, only draw when pressed, unless we are in configuration mode.
        // In fixed mode, always draw.
        if (floatingMode && !isPressed() && !isConfigMode) {
            return;
        }

        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        if (isConfigMode && floatingMode) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x1100FF00); // Faint green background
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        float centerX = getCenterX();
        float centerY = getCenterY();

        // draw outer circle
        if (click_state == CLICK_STATE.SINGLE) {
            paint.setColor(getDefaultColor());
        } else {
            paint.setColor(pressedColor);
        }
        canvas.drawCircle(centerX, centerY, radius_complete, paint);

        paint.setColor(getDefaultColor());
        // draw dead zone (inner center circle) - using a filled style for better visibility if small
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, radius_dead_zone, paint);

        // draw stick depending on state
        paint.setStyle(Paint.Style.STROKE);
        switch (stick_state) {
            case NO_MOVEMENT: {
                paint.setColor(getDefaultColor());
                canvas.drawCircle(centerX, centerY, radius_analog_stick, paint);
                break;
            }
            case MOVED_IN_DEAD_ZONE:
            case MOVED_ACTIVE: {
                paint.setColor(pressedColor);
                canvas.drawCircle(position_stick_x, position_stick_y, radius_analog_stick, paint);
                break;
            }
        }
    }

    private void updatePosition(long eventTime) {
        // get 100% way
        float complete = radius_complete - radius_analog_stick;

        // calculate relative way
        float correlated_y = (float) (Math.sin(Math.PI / 2 - movement_angle) * (movement_radius));
        float correlated_x = (float) (Math.cos(Math.PI / 2 - movement_angle) * (movement_radius));

        // update positions
        position_stick_x = getCenterX() - correlated_x;
        position_stick_y = getCenterY() - correlated_y;

        // Stay active even if we're back in the deadzone because we know the user is actively
        // giving analog stick input and we don't want to snap back into the deadzone.
        // We also release the deadzone if the user keeps the stick pressed for a bit to allow
        // them to make precise movements.
        stick_state = (stick_state == STICK_STATE.MOVED_ACTIVE ||
                eventTime - timeLastClick > timeoutDeadzone ||
                movement_radius > radius_dead_zone) ?
                STICK_STATE.MOVED_ACTIVE : STICK_STATE.MOVED_IN_DEAD_ZONE;

        //  trigger move event if state active
        if (stick_state == STICK_STATE.MOVED_ACTIVE) {
            notifyOnMovement(-correlated_x / complete, correlated_y / complete);
        }
    }

    private boolean isRightStickCameraMode() {
        PreferenceConfiguration config = virtualController.getPrefConfig();
        return floatingMode && elementId == EID_RS && config != null &&
                config.oscRightStickCameraMode;
    }

    private void handleTouchDown(MotionEvent event) {
        CLICK_STATE lastClickState = click_state;

        stick_state = STICK_STATE.MOVED_IN_DEAD_ZONE;
        if (lastClickState == CLICK_STATE.SINGLE &&
                event.getEventTime() - timeLastClick <= timeoutDoubleClick) {
            click_state = CLICK_STATE.DOUBLE;
            notifyOnDoubleClick();
        } else {
            click_state = CLICK_STATE.SINGLE;
            notifyOnClick();
        }

        timeLastClick = event.getEventTime();
        setPressed(true);
    }

    private void resetCameraOutput(boolean notifyListener) {
        boolean hadOutput = cameraOutputX != 0 || cameraOutputY != 0;

        cameraPendingDeltaX = 0;
        cameraPendingDeltaY = 0;
        cameraPendingImpulseX = 0;
        cameraPendingImpulseY = 0;
        cameraLastFrameTimeNanos = 0;
        cameraOutputX = 0;
        cameraOutputY = 0;
        position_stick_x = getCenterX();
        position_stick_y = getCenterY();
        stick_state = isPressed() ? STICK_STATE.MOVED_IN_DEAD_ZONE : STICK_STATE.NO_MOVEMENT;

        if (notifyListener && hadOutput) {
            notifyOnMovement(0, 0);
        }

        invalidate();
    }

    private long getCameraUpdateIntervalMs() {
        PreferenceConfiguration config = virtualController.getPrefConfig();
        int streamFps = config != null && config.fps > 0 ? config.fps : 60;
        int updateRate = Math.min(streamFps, 60);
        return Math.max(8, 1000 / updateRate);
    }

    private void postCameraUpdateRunnable() {
        if (!cameraUpdateRunnablePosted) {
            long now = SystemClock.uptimeMillis();
            long updateIntervalMs = getCameraUpdateIntervalMs();
            if (cameraNextUpdateTimeMs == 0) {
                cameraNextUpdateTimeMs = now + updateIntervalMs;
            } else {
                while (cameraNextUpdateTimeMs <= now) {
                    cameraNextUpdateTimeMs += updateIntervalMs;
                }
            }

            cameraUpdateRunnablePosted = true;
            virtualController.getHandler().postDelayed(
                    cameraUpdateRunnable, cameraNextUpdateTimeMs - now);
        }
    }

    private void removeCameraUpdateRunnable() {
        if (cameraUpdateRunnablePosted) {
            virtualController.getHandler().removeCallbacks(cameraUpdateRunnable);
            cameraUpdateRunnablePosted = false;
        }
        cameraNextUpdateTimeMs = 0;
    }

    private void accumulateCameraSample(float x, float y) {
        cameraPendingDeltaX += x - cameraLastTouchX;
        cameraPendingDeltaY += y - cameraLastTouchY;
        cameraLastTouchX = x;
        cameraLastTouchY = y;
    }

    private void accumulateCameraMovement(MotionEvent event) {
        for (int i = 0; i < event.getHistorySize(); i++) {
            accumulateCameraSample(event.getHistoricalX(i), event.getHistoricalY(i));
        }
        accumulateCameraSample(event.getX(), event.getY());
    }

    private void updateCameraFrame(long frameTimeNanos) {
        float elapsedSeconds = CAMERA_DEFAULT_FRAME_SECONDS;
        if (cameraLastFrameTimeNanos != 0 && frameTimeNanos > cameraLastFrameTimeNanos) {
            elapsedSeconds = (frameTimeNanos - cameraLastFrameTimeNanos) / 1_000_000_000.0f;
        }
        cameraLastFrameTimeNanos = frameTimeNanos;

        float sensitivity = virtualController.getPrefConfig().oscRightStickCameraSensitivity / 100.0f;
        float density = Math.max(0.1f, getResources().getDisplayMetrics().density);
        float deltaToImpulseScale = sensitivity /
                (density * CAMERA_FULL_STICK_SPEED_DP_PER_SECOND);

        // Convert touch distance into a time-equivalent stick impulse. Consuming this impulse over
        // subsequent display frames makes total camera rotation proportional to total finger
        // travel, even when touch and display sample rates differ.
        cameraPendingImpulseX += cameraPendingDeltaX * deltaToImpulseScale;
        cameraPendingImpulseY += -cameraPendingDeltaY * deltaToImpulseScale;
        cameraPendingDeltaX = 0;
        cameraPendingDeltaY = 0;

        float pendingMagnitude = (float) Math.sqrt(
                cameraPendingImpulseX * cameraPendingImpulseX +
                        cameraPendingImpulseY * cameraPendingImpulseY);
        if (pendingMagnitude > CAMERA_MAX_PENDING_IMPULSE_SECONDS) {
            float pendingScale = CAMERA_MAX_PENDING_IMPULSE_SECONDS / pendingMagnitude;
            cameraPendingImpulseX *= pendingScale;
            cameraPendingImpulseY *= pendingScale;
        }

        float deliverySeconds = Math.max(elapsedSeconds, CAMERA_DELIVERY_HORIZON_SECONDS);
        float targetX = cameraPendingImpulseX / deliverySeconds;
        float targetY = cameraPendingImpulseY / deliverySeconds;
        float targetMagnitude = (float) Math.sqrt(targetX * targetX + targetY * targetY);

        if (targetMagnitude > 1.0f) {
            targetX /= targetMagnitude;
            targetY /= targetMagnitude;
        } else if (targetMagnitude < CAMERA_NOISE_FLOOR) {
            targetX = 0;
            targetY = 0;
            cameraPendingImpulseX = 0;
            cameraPendingImpulseY = 0;
        }

        if (targetX != 0 || targetY != 0) {
            cameraPendingImpulseX -= targetX * elapsedSeconds;
            cameraPendingImpulseY -= targetY * elapsedSeconds;
        }

        boolean outputChanged = Float.compare(cameraOutputX, targetX) != 0 ||
                Float.compare(cameraOutputY, targetY) != 0;
        cameraOutputX = targetX;
        cameraOutputY = targetY;

        float maxMovement = radius_complete - radius_analog_stick;
        position_stick_x = getCenterX() + cameraOutputX * maxMovement;
        position_stick_y = getCenterY() - cameraOutputY * maxMovement;
        stick_state = cameraOutputX == 0 && cameraOutputY == 0 ?
                STICK_STATE.MOVED_IN_DEAD_ZONE : STICK_STATE.MOVED_ACTIVE;

        if (outputChanged) {
            notifyOnMovement(cameraOutputX, cameraOutputY);
        }
        invalidate();
    }

    private boolean handleCameraTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                summonedCenterX = event.getX();
                summonedCenterY = event.getY();
                cameraLastTouchX = event.getX();
                cameraLastTouchY = event.getY();
                cameraPendingDeltaX = 0;
                cameraPendingDeltaY = 0;
                cameraPendingImpulseX = 0;
                cameraPendingImpulseY = 0;
                cameraLastFrameTimeNanos = 0;
                cameraOutputX = 0;
                cameraOutputY = 0;
                position_stick_x = summonedCenterX;
                position_stick_y = summonedCenterY;

                removeCameraUpdateRunnable();
                handleTouchDown(event);
                postCameraUpdateRunnable();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (isPressed()) {
                    accumulateCameraMovement(event);
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                removeCameraUpdateRunnable();
                setPressed(false);
                resetCameraOutput(false);
                summonedCenterX = -1;
                summonedCenterY = -1;

                // The concrete stick listener clears both axes and the click flag in one packet.
                notifyOnRevoke();
                break;
            }
        }

        invalidate();
        return true;
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        if (isRightStickCameraMode()) {
            return handleCameraTouchEvent(event);
        }

        if (floatingMode && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            summonedCenterX = event.getX();
            summonedCenterY = event.getY();
        }

        float centerX = getCenterX();
        float centerY = getCenterY();

        // get absolute way for each axis
        relative_x = -(centerX - event.getX());
        relative_y = -(centerY - event.getY());

        // get radius and angel of movement from center
        movement_radius = getMovementRadius(relative_x, relative_y);
        movement_angle = getAngle(relative_x, relative_y);

        float max_movement = radius_complete - radius_analog_stick;
        if (floatingMode && isPressed() && elementId == EID_RS && virtualController.getPrefConfig().oscDynamicCenter && movement_radius > max_movement) {
            float overflow = (float) (movement_radius - max_movement);
            float shiftScale = overflow / (float) movement_radius;
            float shift_x = relative_x * shiftScale;
            float shift_y = relative_y * shiftScale;

            summonedCenterX += shift_x;
            summonedCenterY += shift_y;

            // Recalculate movement relative to new center
            centerX = getCenterX();
            centerY = getCenterY();
            relative_x = -(centerX - event.getX());
            relative_y = -(centerY - event.getY());
            movement_radius = getMovementRadius(relative_x, relative_y);
            movement_angle = getAngle(relative_x, relative_y);
        }

        // pass touch event to parent if out of outer circle
        if (!floatingMode && movement_radius > radius_complete && !isPressed())
            return false;

        // chop radius if out of outer circle or near the edge
        if (movement_radius > (radius_complete - radius_analog_stick)) {
            movement_radius = radius_complete - radius_analog_stick;
        }

        // handle event depending on action
        switch (event.getActionMasked()) {
            // down event (touch event)
            case MotionEvent.ACTION_DOWN: {
                handleTouchDown(event);
                break;
            }
            // up event (revoke touch)
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                setPressed(false);
                summonedCenterX = -1;
                summonedCenterY = -1;
                break;
            }
        }

        if (isPressed()) {
            // when is pressed calculate new positions (will trigger movement if necessary)
            updatePosition(event.getEventTime());
        } else {
            stick_state = STICK_STATE.NO_MOVEMENT;

            // The concrete stick listener clears both axes and the click flag in one packet.
            notifyOnRevoke();
        }
        // refresh view
        invalidate();
        // accept the touch event
        return true;
    }
}
