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
 * These use the touch vibration usage so the system's touch feedback intensity applies, rather
 * than View.performHapticFeedback() which is silenced entirely by the system haptics switch.
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

    /** A clear click for pressing a button down. */
    public void press() {
        // Light: tick, medium: click, strong: heavy click
        vibrate(level);
    }

    /** A lighter tick for sliding onto another button, changing D-pad direction or typing. */
    public void tick() {
        // Light and medium: tick, strong: click
        vibrate(Math.max(level - 1, 1));
    }

    private void vibrate(int strength) {
        if (vibrator == null || level == 0 || rumbleActive) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int effect = strength == 1 ? VibrationEffect.EFFECT_TICK :
                    strength == 2 ? VibrationEffect.EFFECT_CLICK :
                    VibrationEffect.EFFECT_HEAVY_CLICK;
            vibrate(VibrationEffect.createPredefined(effect));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrate(VibrationEffect.createOneShot(5L * strength + 5, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(5L * strength + 5);
        }
    }

    private void vibrate(VibrationEffect effect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(effect, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build());
        }
    }
}
