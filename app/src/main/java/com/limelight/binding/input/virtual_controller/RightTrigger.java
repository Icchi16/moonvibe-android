/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;

public class RightTrigger extends DigitalButton {
    public RightTrigger(final VirtualController controller, final int layer, final Context context) {
        super(controller, EID_RT, layer, context);
        setDefaultAction(GamepadAction.RT);
    }
}
