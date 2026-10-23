package com.limelight.binding.input.virtual_controller;

/**
 * Turns the phone's rotation speed into right stick output, for aiming by tilting the phone.
 *
 * The stick sets how fast the game camera turns, so rotation speed maps to deflection: turning
 * the phone faster turns the camera faster. No Android dependencies, so it can be unit tested.
 */
final class GyroModel {
    /** Rotation speed that gives full stick deflection at 100% sensitivity. */
    static final float FULL_STICK_RAD_PER_SECOND = 2.5f;
    /** Slower than this is the hand's own tremor, not a turn. */
    static final float NOISE_RAD_PER_SECOND = 0.04f;
    /** Weight of the newest sample in the smoothing. */
    static final float SMOOTHING = 0.5f;

    static final class Settings {
        float sensitivity = 1.0f;
        boolean invertY = false;
        /** Turn left and right by tilting like a steering wheel instead of turning the phone. */
        boolean rollForHorizontal = false;
        /** 0 to 1: smallest non-zero output, to get past the game's own deadzone. */
        float antiDeadzone = 0;
    }

    private float smoothX;
    private float smoothY;
    private float outputX;
    private float outputY;

    void reset() {
        smoothX = smoothY = 0;
        outputX = outputY = 0;
    }

    float getOutputX() {
        return outputX;
    }

    float getOutputY() {
        return outputY;
    }

    /**
     * Add one gyroscope sample.
     *
     * @param gx rotation speed around the device's own axes in rad/s, as Android reports them
     *           for the phone held upright: x to the right, y up, z out of the screen
     * @param rotation display rotation in quarter turns: 0, 1 (90), 2 (180) or 3 (270)
     * @return whether the output changed
     */
    boolean addSample(float gx, float gy, float gz, int rotation, Settings settings) {
        // Rotation speed around the screen's current up and right directions
        float aroundUp;
        float aroundRight;
        switch (rotation & 3) {
            case 1:
                aroundUp = gx;
                aroundRight = -gy;
                break;
            case 2:
                aroundUp = -gy;
                aroundRight = -gx;
                break;
            case 3:
                aroundUp = -gx;
                aroundRight = gy;
                break;
            default:
                aroundUp = gy;
                aroundRight = gx;
                break;
        }

        // Turning the phone to the left (counter-clockwise seen from above) turns the camera
        // left; tilting its top edge towards you looks up
        float turn = settings.rollForHorizontal ? gz : aroundUp;
        float rawX = -turn;
        float rawY = settings.invertY ? -aroundRight : aroundRight;

        smoothX += (rawX - smoothX) * SMOOTHING;
        smoothY += (rawY - smoothY) * SMOOTHING;

        float x = 0;
        float y = 0;
        float speed = (float) Math.sqrt(smoothX * smoothX + smoothY * smoothY);
        if (speed > NOISE_RAD_PER_SECOND) {
            // Remove the tremor band from the speed so output starts smoothly from zero
            float scale = (speed - NOISE_RAD_PER_SECOND) / speed *
                    settings.sensitivity / FULL_STICK_RAD_PER_SECOND;
            x = smoothX * scale;
            y = smoothY * scale;

            float magnitude = (float) Math.sqrt(x * x + y * y);
            if (magnitude > 1.0f) {
                x /= magnitude;
                y /= magnitude;
                magnitude = 1.0f;
            }
            if (magnitude > 0 && settings.antiDeadzone > 0) {
                float boosted = settings.antiDeadzone + (1.0f - settings.antiDeadzone) * magnitude;
                x *= boosted / magnitude;
                y *= boosted / magnitude;
            }
        }

        boolean changed = Float.compare(x, outputX) != 0 || Float.compare(y, outputY) != 0;
        outputX = x;
        outputY = y;
        return changed;
    }
}
