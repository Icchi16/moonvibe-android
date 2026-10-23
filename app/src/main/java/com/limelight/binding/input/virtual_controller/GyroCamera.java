package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.WindowManager;

import com.limelight.preferences.PreferenceConfiguration;

/** Reads the gyroscope while gyro aiming is on and feeds it to the right stick. */
class GyroCamera implements SensorEventListener {
    private static final float MOUSE_COUNTS_PER_RADIAN = 800.0f;

    private final Context context;
    private final VirtualController controller;
    private final SensorManager sensorManager;
    private final Sensor gyroscope;
    private final GyroModel model = new GyroModel();
    private final GyroModel.Settings settings = new GyroModel.Settings();

    private boolean listening = false;
    private long lastTimestampNanos = 0;
    private float mouseRemainderX;
    private float mouseRemainderY;

    GyroCamera(Context context, VirtualController controller) {
        this.context = context;
        this.controller = controller;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.gyroscope = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
    }

    boolean isAvailable() {
        return gyroscope != null;
    }

    /** Listen to the gyroscope only while the profile wants it and the controls are shown. */
    void setListening(boolean listen) {
        listen = listen && gyroscope != null;
        if (listen == listening) {
            return;
        }
        listening = listen;
        if (listen) {
            lastTimestampNanos = 0;
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        } else {
            sensorManager.unregisterListener(this);
            model.reset();
            controller.setCameraContribution(this, 0, 0);
        }
    }

    private int displayRotation() {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager != null ? windowManager.getDefaultDisplay() : null;
        return display != null ? display.getRotation() : 0;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        PreferenceConfiguration config = controller.getPrefConfig();
        float elapsed = lastTimestampNanos != 0 ? (event.timestamp - lastTimestampNanos) / 1e9f : 0;
        lastTimestampNanos = event.timestamp;

        if (!controller.isGyroEngaged()) {
            if (model.getOutputX() != 0 || model.getOutputY() != 0) {
                model.reset();
                controller.setCameraContribution(this, 0, 0);
            }
            mouseRemainderX = mouseRemainderY = 0;
            return;
        }

        settings.sensitivity = config.oscGyroSensitivity / 100.0f;
        settings.invertY = config.oscGyroInvertY;
        settings.rollForHorizontal = config.oscGyroRoll;

        if (config.oscRightStickMouseMode) {
            // Mouse camera: move by the angle turned since the last sample
            settings.antiDeadzone = 0;
            model.addSample(event.values[0], event.values[1], event.values[2], displayRotation(), settings);
            float scale = MOUSE_COUNTS_PER_RADIAN * GyroModel.FULL_STICK_RAD_PER_SECOND * elapsed;
            mouseRemainderX += model.getOutputX() * scale;
            mouseRemainderY -= model.getOutputY() * scale;
            int sendX = (int) mouseRemainderX;
            int sendY = (int) mouseRemainderY;
            if (sendX != 0 || sendY != 0) {
                mouseRemainderX -= sendX;
                mouseRemainderY -= sendY;
                controller.sendMouseMove(sendX, sendY);
            }
            return;
        }

        settings.antiDeadzone = config.oscRightStickAntiDeadzone / 100.0f;
        if (model.addSample(event.values[0], event.values[1], event.values[2], displayRotation(), settings)) {
            controller.setCameraContribution(this, model.getOutputX(), model.getOutputY());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
