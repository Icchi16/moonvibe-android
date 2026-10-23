/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class VirtualControllerElement extends View {
    protected static boolean _PRINT_DEBUG_INFORMATION = false;

    public static final int EID_DPAD = 1;
    public static final int EID_LT = 2;
    public static final int EID_RT = 3;
    public static final int EID_LB = 4;
    public static final int EID_RB = 5;
    public static final int EID_A = 6;
    public static final int EID_B = 7;
    public static final int EID_X = 8;
    public static final int EID_Y = 9;
    public static final int EID_BACK = 10;
    public static final int EID_START = 11;
    public static final int EID_LS = 12;
    public static final int EID_RS = 13;
    public static final int EID_LSB = 14;
    public static final int EID_RSB = 15;
    public static final int EID_GDB = 16;

    protected VirtualController virtualController;
    protected final int elementId;

    private final Paint paint = new Paint();

    private int normalColor = 0xF0888888;
    protected int pressedColor = 0xF00000FF;
    private int configMoveColor = 0xF0FF0000;
    private int configResizeColor = 0xF0FF00FF;
    private int configSelectedColor = 0xF000FF00;

    protected int startSize_x;
    protected int startSize_y;

    float position_pressed_x = 0;
    float position_pressed_y = 0;

    private enum Mode {
        Normal,
        Resize,
        Move
    }

    private Mode currentMode = Mode.Normal;

    private static final float SNAP_THRESHOLD_DP = 8.0f;
    /** Holding a grouped button this long before dragging moves it alone. */
    private static final int MOVE_ALONE_HOLD_MS = 500;

    /** Elements being dragged along with this one in the layout editor. */
    private java.util.List<VirtualControllerElement> moveGroup = new java.util.ArrayList<>();
    private boolean dragStarted = false;

    private final Runnable moveAloneRunnable = new Runnable() {
        @Override
        public void run() {
            for (VirtualControllerElement element : moveGroup) {
                if (element != VirtualControllerElement.this) {
                    element.actionCancel();
                }
            }
            moveGroup.clear();
            moveGroup.add(VirtualControllerElement.this);
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    };

    /** Pointer that owns this element; other fingers landing on it are ignored. */
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    protected VirtualControllerElement(VirtualController controller, Context context, int elementId) {
        super(context);

        this.virtualController = controller;
        this.elementId = elementId;
    }

    private float snapX = -1;
    private float snapY = -1;
    private java.util.List<Float> snapLinesX = new java.util.ArrayList<>();
    private java.util.List<Float> snapLinesY = new java.util.ArrayList<>();

    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        // x and y are the coordinates of the current touch relative to the view
        // pressed_x and pressed_y were the touch coordinates at down time
        // The delta movement is (x - pressed_x, y - pressed_y)
        int deltaX = x - pressed_x;
        int deltaY = y - pressed_y;

        int newPos_x = (int) getX() + deltaX;
        int newPos_y = (int) getY() + deltaY;

        snapLinesX.clear();
        snapLinesY.clear();
        snapX = -1;
        snapY = -1;

        if (virtualController.getPrefConfig().oscSnap) {
            // Magnetic pull once within snap range
            float magneticThreshold = SNAP_THRESHOLD_DP * getResources().getDisplayMetrics().density;

            // Ability to snap to original position first
            if (originalPos_x != -1 && originalPos_y != -1) {
                if (Math.abs(newPos_x - originalPos_x) < magneticThreshold) {
                   newPos_x = originalPos_x;
                   snapX = originalPos_x + getWidth() / 2f; // Use center for line
                }
                if (Math.abs(newPos_y - originalPos_y) < magneticThreshold) {
                   newPos_y = originalPos_y;
                   snapY = originalPos_y + getHeight() / 2f; // Use center for line
                }
            }

            // Snap to the centers and edges of other buttons
            for (VirtualControllerElement element : virtualController.getElements()) {
                if (moveGroup.contains(element) || !element.isSnapTarget()) continue;

                float elementCenterX = element.getX() + element.getWidth() / 2f;
                float elementCenterY = element.getY() + element.getHeight() / 2f;

                if (Math.abs((newPos_x + getWidth() / 2f) - elementCenterX) < magneticThreshold) {
                    newPos_x = (int) (elementCenterX - getWidth() / 2f);
                    snapLinesX.add(elementCenterX);
                    snapX = elementCenterX;
                } else if (Math.abs(newPos_x - element.getX()) < magneticThreshold) {
                    newPos_x = (int) element.getX();
                    snapLinesX.add(element.getX());
                } else if (Math.abs((newPos_x + getWidth()) - (element.getX() + element.getWidth())) < magneticThreshold) {
                    newPos_x = (int) (element.getX() + element.getWidth() - getWidth());
                    snapLinesX.add(element.getX() + element.getWidth());
                }

                if (Math.abs((newPos_y + getHeight() / 2f) - elementCenterY) < magneticThreshold) {
                    newPos_y = (int) (elementCenterY - getHeight() / 2f);
                    snapLinesY.add(elementCenterY);
                    snapY = elementCenterY;
                } else if (Math.abs(newPos_y - element.getY()) < magneticThreshold) {
                    newPos_y = (int) element.getY();
                    snapLinesY.add(element.getY());
                } else if (Math.abs((newPos_y + getHeight()) - (element.getY() + element.getHeight())) < magneticThreshold) {
                    newPos_y = (int) (element.getY() + element.getHeight() - getHeight());
                    snapLinesY.add(element.getY() + element.getHeight());
                }
            }
        }

        // Move the whole group by the same amount, stopping when any of it reaches the edge
        int moveX = newPos_x - (int) getX();
        int moveY = newPos_y - (int) getY();
        for (VirtualControllerElement element : moveGroup) {
            moveX = Math.max(moveX, -(int) element.getX());
            moveY = Math.max(moveY, -(int) element.getY());
        }
        for (VirtualControllerElement element : moveGroup) {
            element.setPosition((int) element.getX() + moveX, (int) element.getY() + moveY);
        }
        // Trigger a redraw of the parent to ensure snap lines are visible even outside this view's bounds
        if (getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }

    private void setPosition(int x, int y) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = x;
        layoutParams.topMargin = y;
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;

        requestLayout();
    }

    protected void resizeElement(int pressed_x, int pressed_y, int width, int height) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        int newHeight = height + (startSize_y - pressed_y);
        int newWidth = width + (startSize_x - pressed_x);

        layoutParams.height = newHeight > 20 ? newHeight : 20;
        layoutParams.width = newWidth > 20 ? newWidth : 20;

        requestLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        onElementDraw(canvas);

        if (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons ||
                virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons) {
            paint.setStyle(Paint.Style.STROKE);
            float strokeWidth = getDefaultStrokeWidth();
            paint.setStrokeWidth(strokeWidth);

            if (currentMode == Mode.Normal) {
                paint.setColor(configMoveColor);
            } else {
                paint.setColor(configSelectedColor);
            }

            int inset = (int) strokeWidth / 2;
            canvas.drawRect(inset, inset, getWidth() - inset, getHeight() - inset, paint);
        }

        // Draw snap lines
        if (currentMode == Mode.Move) {
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(2);
            paint.setStyle(Paint.Style.STROKE);

            for (Float xPos : snapLinesX) {
                float localSnapX = xPos - getX();
                canvas.drawLine(localSnapX, -5000, localSnapX, 5000, paint);
            }
            for (Float yPos : snapLinesY) {
                float localSnapY = yPos - getY();
                canvas.drawLine(-5000, localSnapY, 5000, localSnapY, paint);
            }

            // Also draw lines for original position snap if active
            if (snapX != -1 && !snapLinesX.contains(snapX)) {
                float localSnapX = snapX - getX();
                canvas.drawLine(localSnapX, -5000, localSnapX, 5000, paint);
            }
            if (snapY != -1 && !snapLinesY.contains(snapY)) {
                float localSnapY = snapY - getY();
                canvas.drawLine(-5000, localSnapY, 5000, localSnapY, paint);
            }
        }
    }

    /*
    protected void actionShowNormalColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog)
            {}

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                normalColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }

    protected void actionShowPressedColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
            }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                pressedColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }
    */

    private int originalPos_x = -1;
    private int originalPos_y = -1;

    protected void actionEnableMove() {
        currentMode = Mode.Move;
        originalPos_x = (int) getX();
        originalPos_y = (int) getY();
        invalidate();
    }

    protected void actionEnableResize() {
        currentMode = Mode.Resize;
        invalidate();
    }

    protected void actionCancel() {
        // Reset snap state
        snapX = -1;
        snapY = -1;
        snapLinesX.clear();
        snapLinesY.clear();

        currentMode = Mode.Normal;
        invalidate();
        if (getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }

    protected int getDefaultColor() {
        if (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons)
            return configMoveColor;
        else if (virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons)
            return configResizeColor;
        else
            return normalColor;
    }

    protected int getDefaultStrokeWidth() {
        DisplayMetrics screen = getResources().getDisplayMetrics();
        return (int)(screen.heightPixels*0.004f);
    }

    protected void showConfigurationDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());

        alertBuilder.setTitle("Configuration");

        CharSequence functions[] = new CharSequence[]{
                "Move",
                "Resize",
            /*election
            "Set n
            Disable color sormal color",
            "Set pressed color",
            */
                "Cancel"
        };

        alertBuilder.setItems(functions, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: { // move
                        actionEnableMove();
                        break;
                    }
                    case 1: { // resize
                        actionEnableResize();
                        break;
                    }
                /*
                case 2: { // set default color
                    actionShowNormalColorChooser();
                    break;
                }
                case 3: { // set pressed color
                    actionShowPressedColorChooser();
                    break;
                }
                */
                    default: { // cancel
                        actionCancel();
                        break;
                    }
                }
            }
        });
        AlertDialog alert = alertBuilder.create();
        // show menu
        alert.show();
    }

    /**
     * Copies one pointer of a multi-touch event, history included, into a single-pointer event,
     * so elements can keep reading getX() and getHistoricalX() without mixing up fingers.
     */
    private static MotionEvent obtainSinglePointerEvent(MotionEvent event, int pointerIndex, int action) {
        MotionEvent.PointerProperties[] properties = { new MotionEvent.PointerProperties() };
        MotionEvent.PointerCoords[] coords = { new MotionEvent.PointerCoords() };
        event.getPointerProperties(pointerIndex, properties[0]);
        properties[0].id = 0;

        int historySize = event.getHistorySize();
        if (historySize > 0) {
            event.getHistoricalPointerCoords(pointerIndex, 0, coords[0]);
        } else {
            event.getPointerCoords(pointerIndex, coords[0]);
        }

        MotionEvent single = MotionEvent.obtain(event.getDownTime(),
                historySize > 0 ? event.getHistoricalEventTime(0) : event.getEventTime(),
                action, 1, properties, coords, event.getMetaState(), event.getButtonState(),
                event.getXPrecision(), event.getYPrecision(), event.getDeviceId(), 0,
                event.getSource(), event.getFlags());

        if (historySize > 0) {
            for (int h = 1; h < historySize; h++) {
                event.getHistoricalPointerCoords(pointerIndex, h, coords[0]);
                single.addBatch(event.getHistoricalEventTime(h), coords, event.getMetaState());
            }
            event.getPointerCoords(pointerIndex, coords[0]);
            single.addBatch(event.getEventTime(), coords, event.getMetaState());
        }

        return single;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex;
        int elementAction = action;

        // Follow the finger that pressed this element by its pointer id. Reading index 0 would jump
        // to another finger's position once the first one lifts.
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                pointerIndex = 0;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // Ignore secondary touches on controls
                //
                // NB: We can get an additional pointer down if the user touches a non-StreamView area
                // while also touching an OSC control, even if that pointer down doesn't correspond to
                // an area of the OSC control.
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) != activePointerId) {
                    return true;
                }
                // The owning finger lifted while another stays down: release the element now
                pointerIndex = event.getActionIndex();
                elementAction = MotionEvent.ACTION_UP;
                break;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex < 0) {
                    return true;
                }
                break;
            default:
                return true;
        }

        if (elementAction == MotionEvent.ACTION_UP || elementAction == MotionEvent.ACTION_CANCEL) {
            activePointerId = MotionEvent.INVALID_POINTER_ID;
        }

        if (event.getPointerCount() == 1 && elementAction == action) {
            return dispatchElementTouchEvent(event);
        }

        MotionEvent single = obtainSinglePointerEvent(event, pointerIndex, elementAction);
        try {
            return dispatchElementTouchEvent(single);
        } finally {
            single.recycle();
        }
    }

    /** Whether this element can be moved and resized in the layout editor. */
    protected boolean isEditableInLayoutMode() {
        return true;
    }

    /** Whether other elements snap to this one while being moved. */
    protected boolean isSnapTarget() {
        return true;
    }

    private boolean dispatchElementTouchEvent(MotionEvent event) {
        if (virtualController.getControllerMode() == VirtualController.ControllerMode.Active) {
            return onElementTouchEvent(event);
        }

        if (!isEditableInLayoutMode()) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                position_pressed_x = event.getX();
                position_pressed_y = event.getY();
                startSize_x = getWidth();
                startSize_y = getHeight();
                dragStarted = false;

                if (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons) {
                    // A/B/X/Y move as one block; holding still first moves just this button
                    moveGroup = virtualController.getMoveGroup(this);
                    for (VirtualControllerElement element : moveGroup) {
                        element.actionEnableMove();
                    }
                    if (moveGroup.size() > 1) {
                        postDelayed(moveAloneRunnable, MOVE_ALONE_HOLD_MS);
                    }
                }
                else if (virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons)
                    actionEnableResize();

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                switch (currentMode) {
                    case Move: {
                        if (!dragStarted) {
                            float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                            if (Math.hypot(event.getX() - position_pressed_x,
                                    event.getY() - position_pressed_y) < slop) {
                                break;
                            }
                            dragStarted = true;
                            removeCallbacks(moveAloneRunnable);
                        }
                        moveElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Resize: {
                        resizeElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Normal: {
                        break;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                removeCallbacks(moveAloneRunnable);
                for (VirtualControllerElement element : moveGroup) {
                    if (element != this) {
                        element.actionCancel();
                    }
                }
                moveGroup = new java.util.ArrayList<>();
                actionCancel();
                return true;
            }
            default: {
            }
        }
        return true;
    }

    abstract protected void onElementDraw(Canvas canvas);

    abstract public boolean onElementTouchEvent(MotionEvent event);

    protected static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            System.out.println(text);
        }
    }

    public void setColors(int normalColor, int pressedColor) {
        this.normalColor = normalColor;
        this.pressedColor = pressedColor;

        invalidate();
    }


    public void setOpacity(int opacity) {
        int hexOpacity = opacity * 255 / 100;
        this.normalColor = (hexOpacity << 24) | (normalColor & 0x00FFFFFF);
        this.pressedColor = (hexOpacity << 24) | (pressedColor & 0x00FFFFFF);

        invalidate();
    }

    protected final float getPercent(float value, float percent) {
        return value / 100 * percent;
    }

    protected final int getCorrectWidth() {
        return getWidth() > getHeight() ? getHeight() : getWidth();
    }


    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = new JSONObject();

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        configuration.put("LEFT", layoutParams.leftMargin);
        configuration.put("TOP", layoutParams.topMargin);
        configuration.put("WIDTH", layoutParams.width);
        configuration.put("HEIGHT", layoutParams.height);

        return configuration;
    }

    public void loadConfiguration(JSONObject configuration) throws JSONException {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = configuration.getInt("LEFT");
        layoutParams.topMargin = configuration.getInt("TOP");
        layoutParams.width = configuration.getInt("WIDTH");
        layoutParams.height = configuration.getInt("HEIGHT");

        requestLayout();
    }
}
