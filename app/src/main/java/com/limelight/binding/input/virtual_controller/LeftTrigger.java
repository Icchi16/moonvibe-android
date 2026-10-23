/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;

public class LeftTrigger extends DigitalButton {
    public LeftTrigger(final VirtualController controller, final int layer, final Context context) {
        super(controller, EID_LT, layer, context);
        setDefaultAction(GamepadAction.LT);
    }
}
