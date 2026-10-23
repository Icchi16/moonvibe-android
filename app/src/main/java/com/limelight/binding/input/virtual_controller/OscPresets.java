package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Starting layouts offered when creating a profile. */
public final class OscPresets {
    public static final String CURRENT = "current";
    public static final String CLASSIC = "classic";
    public static final String RING = "ring";
    public static final String TRAILS = "trails";

    private static final String[] PRESETS = {CURRENT, CLASSIC, RING, TRAILS};
    private static final int[] NAMES = {
            R.string.osc_preset_current, R.string.osc_preset_classic,
            R.string.osc_preset_ring, R.string.osc_preset_trails
    };
    /** Names suggested for a profile made from each preset; 0 means a numbered name. */
    private static final int[] PROFILE_NAMES = {
            0, R.string.osc_preset_classic_short, R.string.osc_preset_ring_short, R.string.osc_preset_trails_short
    };

    private OscPresets() {
    }

    public static String[] ids() {
        return PRESETS.clone();
    }

    public static String[] names(Context context) {
        String[] names = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            names[i] = context.getString(NAMES[i]);
        }
        return names;
    }

    /** A short name for a profile made from this preset, or null for a numbered one. */
    public static String suggestedProfileName(Context context, String preset) {
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i].equals(preset) && PROFILE_NAMES[i] != 0) {
                return context.getString(PROFILE_NAMES[i]);
            }
        }
        return null;
    }

    /**
     * Replace the live layout with a preset's. Camera, stick and opacity settings are kept from
     * the profile it was copied from.
     *
     * @return whether anything changed
     */
    public static boolean apply(Context context, String preset) {
        if (!CLASSIC.equals(preset) && !RING.equals(preset) && !TRAILS.equals(preset)) {
            return false;
        }

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PreferenceConfiguration.OSC_BUTTON_STYLE_PREF_STRING,
                        CLASSIC.equals(preset) ? PreferenceConfiguration.OSC_BUTTON_STYLE_CLASSIC :
                                PreferenceConfiguration.OSC_BUTTON_STYLE_RING)
                .commit();

        // Back to the default positions, in gamepad mode
        SharedPreferences.Editor layout = context.getSharedPreferences(
                VirtualControllerConfigurationLoader.OSC_PREFERENCE, Context.MODE_PRIVATE).edit().clear();

        if (TRAILS.equals(preset)) {
            // Trails field battles: B attacks, A dodges, Y crafts, X switches tactics,
            // RT starts Brave Rush and L3 locks on. LB dash and LT leader change stay where they are.
            try {
                JSONObject ring = new JSONObject();
                ring.put("COUNT", 5);
                ring.put("PRIMARY", GamepadAction.B);
                JSONArray small = new JSONArray();
                small.put(GamepadAction.A);
                small.put(GamepadAction.Y);
                small.put(GamepadAction.X);
                small.put(GamepadAction.RT);
                small.put(GamepadAction.L3);
                small.put(GamepadAction.LB);
                ring.put("SMALL", small);
                // Without a position the ring keeps its default place and size
                layout.putString(String.valueOf(VirtualControllerElement.EID_RING), ring.toString());
                layout.putString("FL_" + VirtualControllerElement.EID_RING, ring.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        layout.commit();
        return true;
    }
}
