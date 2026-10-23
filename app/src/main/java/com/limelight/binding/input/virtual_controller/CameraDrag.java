package com.limelight.binding.input.virtual_controller;

import android.os.SystemClock;
import android.view.MotionEvent;

import com.limelight.preferences.PreferenceConfiguration;

/**
 * Turns the camera from a finger that is also holding a button (Aim+Fire). Uses the same
 * settings as the touch camera zone and adds its output to the right stick, or sends mouse
 * movement when the camera is in mouse mode.
 */
class CameraDrag {
    private static final long COAST_MAX_MS = 350;
    private static final float MOUSE_COUNTS_PER_DP = 2.0f;

    private final VirtualController controller;
    private final float density;
    private final CameraStickModel model;
    private final CameraStickModel.Settings settings = new CameraStickModel.Settings();

    private boolean active = false;
    private boolean coasting = false;
    private long coastEndTimeMs;
    private float lastX;
    private float lastY;
    private float mouseRemainderX;
    private float mouseRemainderY;

    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            updateFrame();
        }
    };

    CameraDrag(VirtualController controller, float density) {
        this.controller = controller;
        this.density = Math.max(0.1f, density);
        this.model = new CameraStickModel(this.density);
    }

    private PreferenceConfiguration config() {
        return controller.getPrefConfig();
    }

    private boolean isMouseMode() {
        return config() != null && config().oscRightStickMouseMode;
    }

    /** Start turning from this point, in any fixed coordinate space. */
    void start(float x, float y) {
        stopFrames();
        coasting = false;
        active = true;
        model.startTouch(x, y);
        lastX = x;
        lastY = y;
        mouseRemainderX = 0;
        mouseRemainderY = 0;
        if (!isMouseMode()) {
            scheduleFrame();
        }
    }

    void addMovement(MotionEvent event, float offsetX, float offsetY) {
        if (!active) {
            return;
        }
        for (int i = 0; i < event.getHistorySize(); i++) {
            addSample(event.getHistoricalX(i) + offsetX, event.getHistoricalY(i) + offsetY);
        }
        addSample(event.getX() + offsetX, event.getY() + offsetY);
    }

    /** Add one finger position, in the same coordinates as {@link #start}. */
    void addPoint(float x, float y) {
        if (active) {
            addSample(x, y);
        }
    }

    private void addSample(float x, float y) {
        if (isMouseMode()) {
            float scale = MOUSE_COUNTS_PER_DP * sensitivity() / density;
            mouseRemainderX += (x - lastX) * scale;
            mouseRemainderY += (y - lastY) * scale;
            lastX = x;
            lastY = y;
            int sendX = (int) mouseRemainderX;
            int sendY = (int) mouseRemainderY;
            if (sendX != 0 || sendY != 0) {
                mouseRemainderX -= sendX;
                mouseRemainderY -= sendY;
                controller.sendMouseMove(sendX, sendY);
            }
        } else {
            model.addTouchSample(x, y);
        }
    }

    /** The finger lifted: finish the turn it asked for, then let go of the stick. */
    void stop() {
        if (!active) {
            return;
        }
        active = false;
        if (!isMouseMode() && model.hasPendingTurn()) {
            coasting = true;
            coastEndTimeMs = SystemClock.uptimeMillis() + COAST_MAX_MS;
            return;
        }
        release();
    }

    /** Let go at once, for a cancelled touch or a rebuilt layout. */
    void cancel() {
        active = false;
        coasting = false;
        release();
    }

    private void release() {
        stopFrames();
        model.reset();
        controller.setCameraContribution(this, 0, 0);
    }

    private float sensitivity() {
        return config() != null ? config().oscRightStickCameraSensitivity / 100.0f : 1.0f;
    }

    private void updateFrame() {
        if (coasting && (SystemClock.uptimeMillis() >= coastEndTimeMs || !model.hasPendingTurn())) {
            coasting = false;
            release();
            return;
        }
        if (!active && !coasting) {
            return;
        }

        PreferenceConfiguration config = config();
        settings.sensitivity = sensitivity();
        settings.slowBoost = config != null ? config.oscRightStickSlowBoost / 100.0f : 0;
        settings.antiDeadzone = config != null ? config.oscRightStickAntiDeadzone / 100.0f : 0;
        // No zone edge to hold against when aiming from a button
        settings.edgeHold = false;

        if (model.update(SystemClock.uptimeMillis() * 1_000_000L, settings, false, 0, 0)) {
            controller.setCameraContribution(this, model.getOutputX(), model.getOutputY());
        }
        scheduleFrame();
    }

    private void scheduleFrame() {
        PreferenceConfiguration config = config();
        int streamFps = config != null && config.fps > 0 ? config.fps : 60;
        long intervalMs = Math.max(8, 1000 / Math.min(streamFps, 60));
        controller.getHandler().removeCallbacks(frameRunnable);
        controller.getHandler().postDelayed(frameRunnable, intervalMs);
    }

    private void stopFrames() {
        controller.getHandler().removeCallbacks(frameRunnable);
    }
}
