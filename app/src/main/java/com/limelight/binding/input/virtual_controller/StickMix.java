package com.limelight.binding.input.virtual_controller;

/** Adds several right stick sources together, such as the camera zone and an Aim+Fire button. */
final class StickMix {
    private StickMix() {
    }

    /**
     * @param base the stick value from the camera zone, -1 to 1 per axis
     * @param extras further x, y pairs to add
     * @return the summed x and y, cut back to the unit circle when longer
     */
    static float[] combine(float baseX, float baseY, Iterable<float[]> extras) {
        float x = baseX;
        float y = baseY;
        for (float[] extra : extras) {
            x += extra[0];
            y += extra[1];
        }
        float magnitude = (float) Math.sqrt(x * x + y * y);
        if (magnitude > 1.0f) {
            x /= magnitude;
            y /= magnitude;
        }
        return new float[]{x, y};
    }
}
