package com.limelight.binding.input.virtual_controller;

/**
 * Where the buttons of {@link RingButtons} sit and which one a touch belongs to. Kept free of
 * Android classes so it can be unit tested. Lengths are fractions of the element's shorter side.
 */
final class RingGeometry {
    static final int ZONE_NONE = -1;
    static final int ZONE_PRIMARY = 0;

    static final float PRIMARY_OFFSET = 0.32f;
    static final float PRIMARY_RADIUS = 0.18f;
    /** Just past the drawn circle: the big button is easy to hit already. */
    static final float PRIMARY_ZONE = 0.19f;
    static final float SMALL_DISTANCE = 0.44f;
    static final float SMALL_RADIUS = 0.085f;
    /** A little past the outer edge of the small buttons (0.44 + 0.085). */
    static final float OUTER_ZONE = 0.56f;

    private RingGeometry() {
    }

    /** Spacing between neighbouring small buttons, in degrees. */
    static float angleStep(int count) {
        return Math.min(150.0f, 30.0f * (count - 1)) / (count - 1);
    }

    /**
     * Angle of a small button in degrees, measured the way the screen draws: 180 points left
     * and 270 points up. The arc is centred on the up-left diagonal.
     */
    static float smallAngle(int index, int count) {
        float span = angleStep(count) * (count - 1);
        return 225.0f - span / 2 + index * angleStep(count);
    }

    /**
     * Which zone a point falls in, given its offset from the primary button's centre and the
     * element's shorter side.
     */
    static int zoneAt(float dx, float dy, float side, int count) {
        float distance = (float) Math.hypot(dx, dy);
        if (distance <= PRIMARY_ZONE * side) {
            return ZONE_PRIMARY;
        }
        if (distance > OUTER_ZONE * side) {
            return ZONE_NONE;
        }

        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) {
            angle += 360;
        }
        float halfStep = angleStep(count) / 2;
        if (angle < smallAngle(0, count) - halfStep || angle > smallAngle(count - 1, count) + halfStep) {
            return ZONE_NONE;
        }

        int best = 0;
        float bestDifference = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float difference = Math.abs(angle - smallAngle(i, count));
            if (difference < bestDifference) {
                bestDifference = difference;
                best = i;
            }
        }
        return best + 1;
    }
}
