/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

public class DigitalPad extends VirtualControllerElement {
    public final static int DIGITAL_PAD_DIRECTION_NO_DIRECTION = 0;
    int direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
    public final static int DIGITAL_PAD_DIRECTION_LEFT = 1;
    public final static int DIGITAL_PAD_DIRECTION_UP = 2;
    public final static int DIGITAL_PAD_DIRECTION_RIGHT = 4;
    public final static int DIGITAL_PAD_DIRECTION_DOWN = 8;
    List<DigitalPadListener> listeners = new ArrayList<>();

    private static final int DPAD_MARGIN = 5;

    private final Paint paint = new Paint();

    public DigitalPad(VirtualController controller, Context context) {
        super(controller, context, EID_DPAD);
    }

    public void addDigitalPadListener(DigitalPadListener listener) {
        listeners.add(listener);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getCorrectWidth(), 20));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        applyShadow(paint);

        boolean left = (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0;
        boolean up = (direction & DIGITAL_PAD_DIRECTION_UP) > 0;
        boolean right = (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0;
        boolean down = (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0;
        float edge = paint.getStrokeWidth() + DPAD_MARGIN;

        if (direction == DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
            // draw no direction rect
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(getOutlineColor(false));
            canvas.drawRect(
                    getPercent(getWidth(), 36), getPercent(getHeight(), 36),
                    getPercent(getWidth(), 63), getPercent(getHeight(), 63),
                    paint
            );
        }

        // left, up, right and down rects
        drawArm(canvas, edge, getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), getPercent(getHeight(), 66), left);
        drawArm(canvas, getPercent(getWidth(), 33), edge,
                getPercent(getWidth(), 66), getPercent(getHeight(), 33), up);
        drawArm(canvas, getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                getWidth() - edge, getPercent(getHeight(), 66), right);
        drawArm(canvas, getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight() - edge, down);

        // diagonal lines between the arms
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getOutlineColor(left && up));
        canvas.drawLine(edge, getPercent(getHeight(), 33), getPercent(getWidth(), 33), edge, paint);

        paint.setColor(getOutlineColor(up && right));
        canvas.drawLine(getPercent(getWidth(), 66), edge, getWidth() - edge, getPercent(getHeight(), 33), paint);

        paint.setColor(getOutlineColor(right && down));
        canvas.drawLine(getWidth() - paint.getStrokeWidth(), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight() - edge, paint);

        paint.setColor(getOutlineColor(down && left));
        canvas.drawLine(getPercent(getWidth(), 33), getHeight() - edge, edge, getPercent(getHeight(), 66), paint);
    }

    private void drawArm(Canvas canvas, float left, float top, float right, float bottom, boolean pressed) {
        int fillColor = getFillColor(pressed);
        if (fillColor != 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            canvas.drawRect(left, top, right, bottom, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getOutlineColor(pressed));
        canvas.drawRect(left, top, right, bottom, paint);
    }

    private void newDirectionCallback(int direction) {
        _DBG("direction: " + direction);

        // notify listeners
        for (DigitalPadListener listener : listeners) {
            listener.onDirectionChange(direction);
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                int previousDirection = direction;
                direction = 0;

                if (event.getX() < getPercent(getWidth(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_LEFT;
                }
                if (event.getX() > getPercent(getWidth(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_RIGHT;
                }
                if (event.getY() > getPercent(getHeight(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_DOWN;
                }
                if (event.getY() < getPercent(getHeight(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_UP;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    virtualController.getHaptics().press();
                } else if (direction != previousDirection && direction != 0) {
                    virtualController.getHaptics().tick();
                }
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                direction = 0;
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            default: {
            }
        }

        return true;
    }

    public interface DigitalPadListener {
        void onDirectionChange(int direction);
    }
}
