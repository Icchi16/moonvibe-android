package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A big primary button with 3 to 6 smaller buttons on an arc around it, like the action buttons
 * of mobile games. It sits in a bottom right corner.
 *
 * Small buttons are easy to miss on a phone, so touches are not matched against their circles.
 * The circle around the primary button presses it, and the ring around that is cut into slices
 * by angle, each slice belonging to the small button nearest that angle. Touches in the empty
 * corner of the square pass through to whatever is behind, usually the camera.
 */
public class RingButtons extends VirtualControllerElement {
    static final int MIN_SMALL = 3;
    static final int MAX_SMALL = 6;
    static final int DEFAULT_SMALL = 4;

    private static final int[] DEFAULT_SMALL_ACTIONS = {
            GamepadAction.B, GamepadAction.X, GamepadAction.Y,
            GamepadAction.RB, GamepadAction.LB, GamepadAction.RT
    };

    private int smallCount = DEFAULT_SMALL;
    private int primaryAction = GamepadAction.A;
    private final int[] smallActions = DEFAULT_SMALL_ACTIONS.clone();

    /** Zone held by each finger on the ring, by pointer id. */
    private final SparseIntArray pointerZones = new SparseIntArray();
    /** Fingers on each zone: index 0 is the primary button, 1 to 6 the small ones. */
    private final int[] zonePressCounts = new int[MAX_SMALL + 1];

    /** Aim+Fire per zone: after pressing, dragging the same finger turns the camera. */
    private final boolean[] zoneAim = new boolean[MAX_SMALL + 1];
    /** Where each finger on an Aim+Fire zone went down, until it starts aiming. */
    private final SparseArray<float[]> aimDownPoints = new SparseArray<>();
    /** Camera turning for each finger that is aiming. */
    private final SparseArray<CameraDrag> aimDrags = new SparseArray<>();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RingButtons(VirtualController controller, Context context) {
        super(controller, context, EID_RING);
    }

    int getSmallCount() {
        return smallCount;
    }

    void setSmallCount(int count) {
        releaseAll();
        smallCount = Math.max(MIN_SMALL, Math.min(MAX_SMALL, count));
        invalidate();
    }

    int getPrimaryAction() {
        return primaryAction;
    }

    int getSmallAction(int index) {
        return smallActions[index];
    }

    void setPrimaryAction(int action) {
        if (GamepadAction.isValid(action)) {
            releaseAll();
            primaryAction = action;
            invalidate();
        }
    }

    /** The action of a zone: 0 is the big button, 1 to 6 the small ones. */
    int getZoneAction(int zone) {
        return actionForZone(zone);
    }

    void setZoneAction(int zone, int action) {
        if (zone == RingGeometry.ZONE_PRIMARY) {
            setPrimaryAction(action);
        } else {
            setSmallAction(zone - 1, action);
        }
    }

    boolean isZoneAim(int zone) {
        return zoneAim[zone];
    }

    void setZoneAim(int zone, boolean aim) {
        releaseAll();
        zoneAim[zone] = aim;
        invalidate();
    }

    /** Whether a finger is on an Aim+Fire button of the ring, for gyro aiming. */
    boolean isAiming() {
        for (int i = 0; i < pointerZones.size(); i++) {
            if (zoneAim[pointerZones.valueAt(i)]) {
                return true;
            }
        }
        return false;
    }

    void setSmallAction(int index, int action) {
        if (GamepadAction.isValid(action) && index >= 0 && index < MAX_SMALL) {
            releaseAll();
            smallActions[index] = action;
            invalidate();
        }
    }

    // --- Geometry ---

    private float side() {
        return Math.min(getWidth(), getHeight());
    }

    private float primaryCenterX() {
        return getWidth() - RingGeometry.PRIMARY_OFFSET * side();
    }

    private float primaryCenterY() {
        return getHeight() - RingGeometry.PRIMARY_OFFSET * side();
    }

    private int zoneAt(float x, float y) {
        return RingGeometry.zoneAt(x - primaryCenterX(), y - primaryCenterY(), side(), smallCount);
    }

    private int actionForZone(int zone) {
        return zone == RingGeometry.ZONE_PRIMARY ? primaryAction : smallActions[zone - 1];
    }

    // --- Drawing ---

    @Override
    protected void onElementDraw(Canvas canvas) {
        float side = side();
        float cx = primaryCenterX();
        float cy = primaryCenterY();
        paint.setStrokeWidth(getDefaultStrokeWidth());

        drawButton(canvas, cx, cy, RingGeometry.PRIMARY_RADIUS * side, GamepadAction.label(primaryAction),
                zonePressCounts[RingGeometry.ZONE_PRIMARY] > 0, zoneAim[RingGeometry.ZONE_PRIMARY]);
        for (int i = 0; i < smallCount; i++) {
            double angle = Math.toRadians(RingGeometry.smallAngle(i, smallCount));
            float x = cx + (float) Math.cos(angle) * RingGeometry.SMALL_DISTANCE * side;
            float y = cy + (float) Math.sin(angle) * RingGeometry.SMALL_DISTANCE * side;
            drawButton(canvas, x, y, RingGeometry.SMALL_RADIUS * side, GamepadAction.label(smallActions[i]),
                    zonePressCounts[i + 1] > 0, zoneAim[i + 1]);
        }
    }

    private void drawButton(Canvas canvas, float x, float y, float radius, String label,
                            boolean pressed, boolean aim) {
        paint.setColor(pressed ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(x, y, radius - paint.getStrokeWidth(), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(radius * (label.length() > 2 ? 0.45f : 0.7f));
        canvas.drawText(label, x, y + paint.getTextSize() * 0.36f, paint);

        if (aim) {
            // A small dot at the top right marks a button that also aims
            float dot = radius * 0.14f;
            canvas.drawCircle(x + radius * 0.62f, y - radius * 0.62f, dot, paint);
        }
    }

    // --- Touch ---

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (virtualController.getControllerMode() != VirtualController.ControllerMode.Active) {
            return super.onTouchEvent(event);
        }
        return onElementTouchEvent(event);
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                int zone = zoneAt(event.getX(index), event.getY(index));
                if (zone == RingGeometry.ZONE_NONE) {
                    // Let a first finger in the empty corner reach the controls behind
                    return action != MotionEvent.ACTION_DOWN;
                }
                pointerZones.put(event.getPointerId(index), zone);
                if (zoneAim[zone]) {
                    aimDownPoints.put(event.getPointerId(index),
                            new float[]{event.getX(index), event.getY(index)});
                }
                pressZone(zone);
                virtualController.getHaptics().press();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pointerId = event.getPointerId(i);
                    int oldZone = pointerZones.get(pointerId, RingGeometry.ZONE_NONE);
                    if (oldZone == RingGeometry.ZONE_NONE) {
                        continue;
                    }
                    if (zoneAim[oldZone]) {
                        // An Aim+Fire button stays held and the finger turns the camera
                        trackAim(pointerId, event.getX(i), event.getY(i));
                        continue;
                    }
                    int newZone = zoneAt(event.getX(i), event.getY(i));
                    // Sliding onto another button switches to it; drifting off the ring keeps
                    // the button held, which forgives a thumb that wanders during a combo
                    if (newZone != RingGeometry.ZONE_NONE && newZone != oldZone) {
                        releaseZone(oldZone);
                        pointerZones.put(pointerId, newZone);
                        pressZone(newZone);
                        virtualController.getHaptics().tick();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int pointerId = event.getPointerId(event.getActionIndex());
                int zone = pointerZones.get(pointerId, RingGeometry.ZONE_NONE);
                if (zone != RingGeometry.ZONE_NONE) {
                    pointerZones.delete(pointerId);
                    finishAim(pointerId, true);
                    releaseZone(zone);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                // The last finger lifted: let its turn finish like a swipe on the camera zone
                finishAim(event.getPointerId(event.getActionIndex()), true);
                releaseAll();
                return true;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean isPressed() {
        return pointerZones.size() > 0;
    }

    private void pressZone(int zone) {
        zonePressCounts[zone]++;
        virtualController.setActionPressed(actionForZone(zone), true);
        invalidate();
    }

    private void releaseZone(int zone) {
        if (zonePressCounts[zone] > 0) {
            zonePressCounts[zone]--;
            virtualController.setActionPressed(actionForZone(zone), false);
        }
        invalidate();
    }

    private void trackAim(int pointerId, float x, float y) {
        CameraDrag drag = aimDrags.get(pointerId);
        if (drag != null) {
            drag.addPoint(x, y);
            return;
        }
        float[] down = aimDownPoints.get(pointerId);
        if (down == null) {
            return;
        }
        float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        if (Math.hypot(x - down[0], y - down[1]) >= slop) {
            // Start turning from here, so the slop itself causes no jump
            drag = new CameraDrag(virtualController, getResources().getDisplayMetrics().density);
            drag.start(x, y);
            aimDrags.put(pointerId, drag);
            aimDownPoints.remove(pointerId);
        }
    }

    private void finishAim(int pointerId, boolean coast) {
        aimDownPoints.remove(pointerId);
        CameraDrag drag = aimDrags.get(pointerId);
        if (drag != null) {
            aimDrags.remove(pointerId);
            if (coast) {
                drag.stop();
            } else {
                drag.cancel();
            }
        }
    }

    private void releaseAll() {
        for (int i = 0; i < pointerZones.size(); i++) {
            releaseZone(pointerZones.valueAt(i));
        }
        pointerZones.clear();
        for (int i = 0; i < aimDrags.size(); i++) {
            aimDrags.valueAt(i).cancel();
        }
        aimDrags.clear();
        aimDownPoints.clear();
    }

    // --- Saving ---

    @Override
    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = super.getConfiguration();
        configuration.put("COUNT", smallCount);
        configuration.put("PRIMARY", primaryAction);
        JSONArray small = new JSONArray();
        for (int smallAction : smallActions) {
            small.put(smallAction);
        }
        configuration.put("SMALL", small);
        JSONArray aim = new JSONArray();
        for (boolean zoneAimed : zoneAim) {
            aim.put(zoneAimed);
        }
        configuration.put("AIM", aim);
        return configuration;
    }

    @Override
    public void loadConfiguration(JSONObject configuration) throws JSONException {
        super.loadConfiguration(configuration);
        releaseAll();
        smallCount = Math.max(MIN_SMALL, Math.min(MAX_SMALL, configuration.optInt("COUNT", DEFAULT_SMALL)));
        int savedPrimary = configuration.optInt("PRIMARY", GamepadAction.A);
        primaryAction = GamepadAction.isValid(savedPrimary) ? savedPrimary : GamepadAction.A;
        JSONArray small = configuration.optJSONArray("SMALL");
        for (int i = 0; i < MAX_SMALL; i++) {
            int savedAction = small != null ? small.optInt(i, DEFAULT_SMALL_ACTIONS[i]) : DEFAULT_SMALL_ACTIONS[i];
            smallActions[i] = GamepadAction.isValid(savedAction) ? savedAction : DEFAULT_SMALL_ACTIONS[i];
        }
        JSONArray aim = configuration.optJSONArray("AIM");
        for (int i = 0; i < zoneAim.length; i++) {
            zoneAim[i] = aim != null && aim.optBoolean(i, false);
        }
        invalidate();
    }
}
