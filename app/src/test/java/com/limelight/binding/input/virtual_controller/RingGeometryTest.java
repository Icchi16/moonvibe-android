package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RingGeometryTest {
    private static final float SIDE = 100;

    /** Offset from the primary button's centre at this angle (screen degrees) and distance. */
    private static int zone(float angleDegrees, float distance, int count) {
        double angle = Math.toRadians(angleDegrees);
        return RingGeometry.zoneAt((float) Math.cos(angle) * distance, (float) Math.sin(angle) * distance, SIDE, count);
    }

    @Test
    public void fourButtonsSpanLeftToUp() {
        assertEquals(180, RingGeometry.smallAngle(0, 4), 0.001);
        assertEquals(270, RingGeometry.smallAngle(3, 4), 0.001);
    }

    @Test
    public void arcStaysCentredOnTheDiagonal() {
        for (int count = RingButtons.MIN_SMALL; count <= RingButtons.MAX_SMALL; count++) {
            float first = RingGeometry.smallAngle(0, count);
            float last = RingGeometry.smallAngle(count - 1, count);
            assertEquals(225, (first + last) / 2, 0.001);
        }
    }

    @Test
    public void centreIsThePrimaryButton() {
        assertEquals(RingGeometry.ZONE_PRIMARY, RingGeometry.zoneAt(0, 0, SIDE, 4));
        assertEquals(RingGeometry.ZONE_PRIMARY, zone(30, 18, 4));
    }

    @Test
    public void touchOnASmallButtonPicksIt() {
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, zone(RingGeometry.smallAngle(i, 4), 44, 4));
        }
    }

    @Test
    public void missOutsideTheButtonStillPicksTheNearestSlice() {
        // Between the second and third button, closer to the second, near the outer edge
        assertEquals(2, zone(222, 54, 4));
        assertEquals(3, zone(228, 30, 4));
    }

    @Test
    public void emptyCornerAndFarTouchesPassThrough() {
        // Down and to the right of the primary button there are no small buttons
        assertEquals(RingGeometry.ZONE_NONE, zone(45, 40, 4));
        // Beyond the outer edge of the ring
        assertEquals(RingGeometry.ZONE_NONE, zone(225, 60, 4));
    }

    @Test
    public void sliceEdgesSitHalfwayToTheNextButton() {
        float step = RingGeometry.angleStep(6);
        assertEquals(1, zone(RingGeometry.smallAngle(0, 6) - step / 2 + 0.5f, 44, 6));
        assertEquals(RingGeometry.ZONE_NONE, zone(RingGeometry.smallAngle(0, 6) - step / 2 - 0.5f, 44, 6));
        assertEquals(6, zone(RingGeometry.smallAngle(5, 6) + step / 2 - 0.5f, 44, 6));
    }
}
