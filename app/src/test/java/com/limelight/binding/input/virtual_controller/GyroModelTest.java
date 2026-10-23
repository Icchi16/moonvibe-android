package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GyroModelTest {
    private static GyroModel settled(float gx, float gy, float gz, int rotation, GyroModel.Settings settings) {
        GyroModel model = new GyroModel();
        // Let the smoothing settle on a steady rotation
        for (int i = 0; i < 30; i++) {
            model.addSample(gx, gy, gz, rotation, settings);
        }
        return model;
    }

    @Test
    public void stillPhoneGivesNoOutput() {
        GyroModel model = settled(0.02f, -0.01f, 0.01f, 1, new GyroModel.Settings());
        assertEquals(0, model.getOutputX(), 0);
        assertEquals(0, model.getOutputY(), 0);
    }

    @Test
    public void turningLeftInLandscapeTurnsCameraLeft() {
        // Rotation 90: the screen's up direction is the device's +x axis. Turning the phone left
        // is counter-clockwise around it seen from above, a positive speed.
        GyroModel model = settled(1.0f, 0, 0, 1, new GyroModel.Settings());
        assertTrue(model.getOutputX() < 0);
        assertEquals(0, model.getOutputY(), 1e-6);
    }

    @Test
    public void otherLandscapeIsMirrored() {
        GyroModel left = settled(1.0f, 0, 0, 1, new GyroModel.Settings());
        GyroModel right = settled(-1.0f, 0, 0, 3, new GyroModel.Settings());
        assertEquals(left.getOutputX(), right.getOutputX(), 1e-6);
    }

    @Test
    public void tiltingTopEdgeTowardsYouLooksUp() {
        // Rotation 90: the screen's right is the device's -y axis
        GyroModel model = settled(0, -1.0f, 0, 1, new GyroModel.Settings());
        assertTrue(model.getOutputY() > 0);

        GyroModel.Settings inverted = new GyroModel.Settings();
        inverted.invertY = true;
        assertTrue(settled(0, -1.0f, 0, 1, inverted).getOutputY() < 0);
    }

    @Test
    public void rollSteersWhenChosen() {
        GyroModel.Settings settings = new GyroModel.Settings();
        settings.rollForHorizontal = true;
        GyroModel model = settled(0, 0, 1.0f, 1, settings);
        assertTrue(model.getOutputX() < 0);
    }

    @Test
    public void fastTurnSaturatesAndSensitivityScales() {
        GyroModel fast = settled(10f, 0, 0, 1, new GyroModel.Settings());
        assertEquals(-1.0f, fast.getOutputX(), 1e-5);

        GyroModel.Settings doubled = new GyroModel.Settings();
        doubled.sensitivity = 2.0f;
        float normal = settled(0.5f, 0, 0, 1, new GyroModel.Settings()).getOutputX();
        float twice = settled(0.5f, 0, 0, 1, doubled).getOutputX();
        assertEquals(2 * normal, twice, 1e-4);
    }

    @Test
    public void antiDeadzoneLiftsSmallTurns() {
        GyroModel.Settings settings = new GyroModel.Settings();
        settings.antiDeadzone = 0.2f;
        float output = Math.abs(settled(0.1f, 0, 0, 1, settings).getOutputX());
        assertTrue(output >= 0.2f);
    }
}
