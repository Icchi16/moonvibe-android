package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Short vibrations that confirm touches on the on-screen controls and keyboard.
 *
 * The system's predefined click effects are too faint to notice mid-game on phones like the
 * Galaxy A52, and the touch vibration usage is scaled by (or muted with) the touch feedback
 * setting. So these are plain pulses with a set length and strength, played with the same media
 * usage as game rumble.
 */
public class TouchHaptics {
    public static final String LEVEL_OFF = "off";
    public static final String LEVEL_LIGHT = "light";
    public static final String LEVEL_MEDIUM = "medium";
    public static final String LEVEL_STRONG = "strong";

    // Game rumble and touch feedback share the phone's motor. A touch vibration would cut a
    // running rumble short, and the rumble already tells the hand something happened.
    private static volatile boolean rumbleActive = false;

    private final Vibrator vibrator;
    private final int level;

    public TouchHaptics(Context context, String level) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.vibrator = vibrator != null && vibrator.hasVibrator() ? vibrator : null;

        if (LEVEL_LIGHT.equals(level)) {
            this.level = 1;
        } else if (LEVEL_MEDIUM.equals(level)) {
            this.level = 2;
        } else if (LEVEL_STRONG.equals(level)) {
            this.level = 3;
        } else {
            this.level = 0;
        }
    }

    /** Called by the rumble code whenever it starts or stops vibrating the phone. */
    public static void setRumbleActive(boolean active) {
        rumbleActive = active;
    }

    // Pulse length (ms) and amplitude (1-255) for Light, Medium and Strong
    private static final long[] PRESS_MS = {14, 27, 32};
    private static final int[] PRESS_AMPLITUDE = {140, 235, 255};
    private static final long[] TICK_MS = {8, 15, 18};
    private static final int[] TICK_AMPLITUDE = {100, 190, 230};

    /** A clear pulse for pressing a button down. */
    public void press() {
        if (level > 0) {
            vibrate(PRESS_MS[level - 1], PRESS_AMPLITUDE[level - 1]);
        }
    }

    /** A shorter pulse for sliding onto another button, changing D-pad direction or typing. */
    public void tick() {
        if (level > 0) {
            vibrate(TICK_MS[level - 1], TICK_AMPLITUDE[level - 1]);
        }
    }

    private void vibrate(long durationMs, int amplitude) {
        if (vibrator == null || rumbleActive) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!vibrator.hasAmplitudeControl()) {
                // Without amplitude control a longer pulse is the only way to feel stronger
                amplitude = VibrationEffect.DEFAULT_AMPLITUDE;
                durationMs += durationMs / 2;
            }
            vibrate(VibrationEffect.createOneShot(durationMs, amplitude));
        } else {
            vibrator.vibrate(durationMs);
        }
    }

    private void vibrate(VibrationEffect effect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_MEDIA)
                    .build());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(effect, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build());
        }
    }
}
