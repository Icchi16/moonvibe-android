package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class CameraStickModelTest {
    private static final long FRAME_NANOS = 16_666_667L;
    private static final float FRAME_SECONDS = FRAME_NANOS / 1_000_000_000.0f;

    private CameraStickModel model;
    private CameraStickModel.Settings settings;
    private long now;

    @Before
    public void setUp() {
        // One pixel per dp keeps the numbers readable
        model = new CameraStickModel(1.0f);
        settings = new CameraStickModel.Settings();
        now = 1_000_000_000L;
        model.startTouch(0, 0);
    }

    /** Run output frames until the pending turn is used up, and return the total rotation. */
    private float[] runUntilIdle() {
        float rotationX = 0;
        float rotationY = 0;
        float maxMagnitude = 0;
        for (int frame = 0; frame < 1000 && model.hasPendingTurn(); frame++) {
            now += FRAME_NANOS;
            model.update(now, settings, false, 0, 0);
            rotationX += model.getOutputX() * FRAME_SECONDS;
            rotationY += model.getOutputY() * FRAME_SECONDS;
            maxMagnitude = Math.max(maxMagnitude, (float) Math.hypot(model.getOutputX(), model.getOutputY()));
        }
        return new float[]{rotationX, rotationY, maxMagnitude};
    }

    private void swipe(float toX, float toY, int samples) {
        for (int i = 1; i <= samples; i++) {
            model.addTouchSample(toX * i / samples, toY * i / samples);
        }
    }

    @Test
    public void rotationMatchesFingerTravel() {
        swipe(60, 0, 6);
        float[] result = runUntilIdle();
        // 60 dp at 600 dp/s for full deflection is 0.1 s of full stick
        assertEquals(0.1f, result[0], 0.001f);
        assertEquals(0, result[1], 0.0001f);
    }

    @Test
    public void doubleTravelGivesDoubleRotation() {
        swipe(30, 0, 3);
        float single = runUntilIdle()[0];

        model.startTouch(0, 0);
        swipe(60, 0, 3);
        float twice = runUntilIdle()[0];

        assertEquals(2 * single, twice, 0.001f);
    }

    @Test
    public void sensitivityScalesRotation() {
        settings.sensitivity = 2.0f;
        swipe(30, 0, 3);
        assertEquals(0.1f, runUntilIdle()[0], 0.001f);
    }

    @Test
    public void swipingUpTurnsUp() {
        swipe(0, -60, 6);
        float[] result = runUntilIdle();
        assertTrue(result[1] > 0);
    }

    @Test
    public void restingFingerJitterIsIgnored() {
        for (int i = 0; i < 50; i++) {
            model.addTouchSample(i % 2 == 0 ? 0.2f : -0.2f, 0);
        }
        assertFalse(model.hasPendingTurn());
    }

    @Test
    public void slowSwipeStillGetsThrough() {
        for (int i = 1; i <= 10; i++) {
            model.addTouchSample(0.2f * i, 0);
        }
        assertTrue(model.hasPendingTurn());
        // At least 1.6 of the 2 dp gets through: steps are measured from the last accepted point
        assertTrue(runUntilIdle()[0] >= 1.6f / CameraStickModel.FULL_STICK_SPEED_DP_PER_SECOND - 0.0001f);
    }

    @Test
    public void outputNeverExceedsFullDeflection() {
        swipe(400, 300, 2);
        float[] result = runUntilIdle();
        assertTrue(result[2] <= 1.0001f);
    }

    @Test
    public void hugeFlickIsCapped() {
        swipe(5000, 0, 1);
        float[] result = runUntilIdle();
        assertTrue(result[0] <= CameraStickModel.MAX_PENDING_IMPULSE_SECONDS + 0.001f);
    }

    @Test
    public void antiDeadzoneLiftsSmallOutput() {
        settings.antiDeadzone = 0.2f;
        swipe(2, 0, 1);
        now += FRAME_NANOS;
        model.update(now, settings, false, 0, 0);
        assertTrue(model.getOutputX() >= 0.2f);
    }

    @Test
    public void slowBoostLiftsSlowSwipes() {
        swipe(3, 0, 1);
        now += FRAME_NANOS;
        model.update(now, settings, false, 0, 0);
        float plain = model.getOutputX();

        model.startTouch(0, 0);
        settings.slowBoost = 1.0f;
        swipe(3, 0, 1);
        now += FRAME_NANOS;
        model.update(now, settings, false, 0, 0);

        assertTrue(model.getOutputX() > plain);
    }

    @Test
    public void edgeHoldKeepsTurningOnlyWhenEnabled() {
        now += FRAME_NANOS;
        model.update(now, settings, true, 1, 0);
        assertEquals(0, model.getOutputX(), 0);

        settings.edgeHold = true;
        now += FRAME_NANOS;
        model.update(now, settings, true, 1, 0);
        assertEquals(CameraStickModel.EDGE_HOLD_OUTPUT, model.getOutputX(), 0.0001f);
    }

    @Test
    public void resetClearsEverything() {
        swipe(60, 0, 6);
        now += FRAME_NANOS;
        model.update(now, settings, false, 0, 0);
        model.reset();
        assertFalse(model.hasPendingTurn());
        assertEquals(0, model.getOutputX(), 0);
        assertEquals(0, model.getOutputY(), 0);
    }

    @Test
    public void updateReportsWhetherOutputChanged() {
        now += FRAME_NANOS;
        assertFalse(model.update(now, settings, false, 0, 0));
        swipe(30, 0, 1);
        now += FRAME_NANOS;
        assertTrue(model.update(now, settings, false, 0, 0));
    }
}
