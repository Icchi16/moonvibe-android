package com.limelight.binding.input.virtual_controller;

/**
 * Turns finger movement on the touch camera into right stick output.
 *
 * Finger travel becomes a stick "impulse" (deflection multiplied by time), delivered over the
 * next few output frames. Total camera rotation then follows total finger travel, however
 * irregularly touch events arrive. This class has no Android dependencies so it can be unit
 * tested; AnalogStick feeds it touch samples and calls {@link #update} once per output frame.
 */
final class CameraStickModel {
    /** Finger speed that produces full stick deflection at 100% camera sensitivity. */
    static final float FULL_STICK_SPEED_DP_PER_SECOND = 600.0f;
    static final float DEFAULT_FRAME_SECONDS = 1.0f / 60.0f;
    /** Spread touch delta across a few frames to bridge irregular MotionEvent delivery. */
    static final float DELIVERY_HORIZON_SECONDS = 0.045f;
    static final float MAX_PENDING_IMPULSE_SECONDS = 0.3f;
    static final float NOISE_FLOOR = 0.002f;
    /** Finger travel below this is treated as touch jitter rather than a slow swipe. */
    static final float JITTER_DP = 0.3f;
    /** Stick output added while the finger is held at the edge of the zone, so the camera keeps turning. */
    static final float EDGE_HOLD_OUTPUT = 0.6f;

    /** User settings that shape the output. */
    static final class Settings {
        /** 1.0 is 100% sensitivity. */
        float sensitivity = 1.0f;
        /** 0 to 1: how much slow swipes are lifted by a power curve. */
        float slowBoost = 0;
        /** 0 to 1: smallest non-zero output, to get past the game's own deadzone. */
        float antiDeadzone = 0;
        boolean edgeHold = false;
    }

    private final float density;

    private float lastTouchX;
    private float lastTouchY;
    private float pendingDeltaX;
    private float pendingDeltaY;
    private float pendingImpulseX;
    private float pendingImpulseY;
    private long lastFrameTimeNanos;
    private float outputX;
    private float outputY;

    /** @param density screen pixels per dp */
    CameraStickModel(float density) {
        this.density = Math.max(0.1f, density);
    }

    /** Forget everything and start a new touch at this point. */
    void startTouch(float x, float y) {
        reset();
        lastTouchX = x;
        lastTouchY = y;
    }

    void reset() {
        pendingDeltaX = 0;
        pendingDeltaY = 0;
        pendingImpulseX = 0;
        pendingImpulseY = 0;
        lastFrameTimeNanos = 0;
        outputX = 0;
        outputY = 0;
    }

    /** Add one finger position, in pixels. */
    void addTouchSample(float x, float y) {
        float deltaX = x - lastTouchX;
        float deltaY = y - lastTouchY;

        // Measure from the last accepted point, so a resting finger's jitter never adds up
        // while a slow swipe still gets through once it has travelled far enough.
        float jitterPx = JITTER_DP * density;
        if (deltaX * deltaX + deltaY * deltaY >= jitterPx * jitterPx) {
            lastTouchX = x;
            lastTouchY = y;
            pendingDeltaX += deltaX;
            pendingDeltaY += deltaY;
        }
    }

    /** Whether finger travel is still waiting to be turned into output. */
    boolean hasPendingTurn() {
        float pendingMagnitude = (float) Math.sqrt(
                pendingImpulseX * pendingImpulseX + pendingImpulseY * pendingImpulseY);
        return pendingDeltaX != 0 || pendingDeltaY != 0 ||
                pendingMagnitude >= NOISE_FLOOR * DELIVERY_HORIZON_SECONDS;
    }

    /** Stick X, -1 to 1, right is positive. */
    float getOutputX() {
        return outputX;
    }

    /** Stick Y, -1 to 1, up is positive. */
    float getOutputY() {
        return outputY;
    }

    /**
     * Compute the stick output for one frame.
     *
     * @param edgeDirX unit direction the finger pushes at the zone's edge, stick axes
     * @return whether the output changed
     */
    boolean update(long frameTimeNanos, Settings settings,
                   boolean atEdge, float edgeDirX, float edgeDirY) {
        float elapsedSeconds = DEFAULT_FRAME_SECONDS;
        if (lastFrameTimeNanos != 0 && frameTimeNanos > lastFrameTimeNanos) {
            elapsedSeconds = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000.0f;
        }
        lastFrameTimeNanos = frameTimeNanos;

        float deltaToImpulseScale = settings.sensitivity /
                (density * FULL_STICK_SPEED_DP_PER_SECOND);

        // Convert touch distance into a time-equivalent stick impulse. Consuming this impulse over
        // subsequent display frames makes total camera rotation proportional to total finger
        // travel, even when touch and display sample rates differ.
        pendingImpulseX += pendingDeltaX * deltaToImpulseScale;
        // Screen Y grows downward, stick Y grows upward
        pendingImpulseY += -pendingDeltaY * deltaToImpulseScale;
        pendingDeltaX = 0;
        pendingDeltaY = 0;

        float pendingMagnitude = (float) Math.sqrt(
                pendingImpulseX * pendingImpulseX + pendingImpulseY * pendingImpulseY);
        if (pendingMagnitude > MAX_PENDING_IMPULSE_SECONDS) {
            float pendingScale = MAX_PENDING_IMPULSE_SECONDS / pendingMagnitude;
            pendingImpulseX *= pendingScale;
            pendingImpulseY *= pendingScale;
        }

        float deliverySeconds = Math.max(elapsedSeconds, DELIVERY_HORIZON_SECONDS);
        float targetX = pendingImpulseX / deliverySeconds;
        float targetY = pendingImpulseY / deliverySeconds;
        float targetMagnitude = (float) Math.sqrt(targetX * targetX + targetY * targetY);

        if (targetMagnitude > 1.0f) {
            targetX /= targetMagnitude;
            targetY /= targetMagnitude;
        } else if (targetMagnitude < NOISE_FLOOR) {
            // Keep the pending impulse so a very slow swipe builds up instead of being dropped
            targetX = 0;
            targetY = 0;
        }

        if (targetX != 0 || targetY != 0) {
            pendingImpulseX -= targetX * elapsedSeconds;
            pendingImpulseY -= targetY * elapsedSeconds;
        }

        // Lift slow swipes with a power curve; fast swipes near full deflection barely change
        targetMagnitude = (float) Math.sqrt(targetX * targetX + targetY * targetY);
        float slowBoostExponent = 1.0f - 0.5f * settings.slowBoost;
        if (targetMagnitude > 0 && targetMagnitude < 1.0f && slowBoostExponent < 1.0f) {
            float boostedMagnitude = (float) Math.pow(targetMagnitude, slowBoostExponent);
            targetX *= boostedMagnitude / targetMagnitude;
            targetY *= boostedMagnitude / targetMagnitude;
        }

        // Holding the finger against the ring keeps turning, like a regular stick at its edge
        if (atEdge && settings.edgeHold) {
            targetX += edgeDirX * EDGE_HOLD_OUTPUT;
            targetY += edgeDirY * EDGE_HOLD_OUTPUT;
        }

        targetMagnitude = (float) Math.sqrt(targetX * targetX + targetY * targetY);
        if (targetMagnitude > 1.0f) {
            targetX /= targetMagnitude;
            targetY /= targetMagnitude;
            targetMagnitude = 1.0f;
        }

        // Games ignore small stick values inside their own deadzone. Start any non-zero output
        // just past it so slow, precise swipes still move the camera.
        if (targetMagnitude > 0 && settings.antiDeadzone > 0) {
            float boostedMagnitude = settings.antiDeadzone + (1.0f - settings.antiDeadzone) * targetMagnitude;
            targetX *= boostedMagnitude / targetMagnitude;
            targetY *= boostedMagnitude / targetMagnitude;
        }

        boolean outputChanged = Float.compare(outputX, targetX) != 0 ||
                Float.compare(outputY, targetY) != 0;
        outputX = targetX;
        outputY = targetY;
        return outputChanged;
    }
}
