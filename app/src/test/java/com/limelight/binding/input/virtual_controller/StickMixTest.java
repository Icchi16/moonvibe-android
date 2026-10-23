package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class StickMixTest {
    @Test
    public void baseAloneIsUnchanged() {
        float[] mixed = StickMix.combine(0.3f, -0.2f, Collections.<float[]>emptyList());
        assertEquals(0.3f, mixed[0], 1e-6);
        assertEquals(-0.2f, mixed[1], 1e-6);
    }

    @Test
    public void sourcesAddUp() {
        float[] mixed = StickMix.combine(0.2f, 0, Arrays.asList(new float[]{0.3f, 0.1f}, new float[]{-0.1f, 0.2f}));
        assertEquals(0.4f, mixed[0], 1e-6);
        assertEquals(0.3f, mixed[1], 1e-6);
    }

    @Test
    public void sumIsCutBackToFullDeflectionKeepingDirection() {
        float[] mixed = StickMix.combine(0.9f, 0, Collections.singletonList(new float[]{0.9f, 0.9f}));
        assertEquals(1.0f, Math.hypot(mixed[0], mixed[1]), 1e-5);
        assertEquals(2.0f, mixed[0] / mixed[1], 1e-5);
    }
}
