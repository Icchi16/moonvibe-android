package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Named sets of on-screen control settings, such as one for the desktop and one per game.
 *
 * The rest of the app keeps reading its usual preference keys. Those keys always hold the active
 * profile, so the copy stored here for the active profile can be out of date; it is refreshed
 * from the live keys before anything reads or copies it. Switching writes the chosen profile's
 * values back into the live keys.
 */
public class OscProfiles {
    private static final String PROFILES_PREFERENCE = "OSC_PROFILES";
    private static final String ORDER_KEY = "order";
    private static final String ACTIVE_KEY = "active";
    private static final String NEXT_ID_KEY = "next_id";
    private static final String NAME_PREFIX = "name_";
    private static final String DATA_PREFIX = "data_";

    private static final String DATA_SETTINGS = "settings";
    private static final String DATA_LAYOUT = "layout";

    /** Settings that belong to a profile. Everything else, like haptics and video, is shared. */
    private static final String[] PROFILE_SETTING_KEYS = {
            "checkbox_only_show_L3R3",
            "checkbox_show_guide_button",
            PreferenceConfiguration.OSC_OPACITY_PREF_STRING,
            PreferenceConfiguration.OSC_TOUCH_ASSIST_PREF_STRING,
            PreferenceConfiguration.OSC_FLOATING_JOYSTICK_PREF_STRING,
            PreferenceConfiguration.OSC_FLOATING_JOYSTICK_SIZE_PREF_STRING,
            PreferenceConfiguration.OSC_DYNAMIC_CENTER_PREF_STRING,
            PreferenceConfiguration.OSC_EDIT_STICK_ZONES_PREF_STRING,
            PreferenceConfiguration.OSC_SNAP_PREF_STRING,
            PreferenceConfiguration.OSC_RIGHT_STICK_MODE_PREF_STRING,
            PreferenceConfiguration.OSC_RIGHT_STICK_CAMERA_SENSITIVITY_PREF_STRING,
            PreferenceConfiguration.OSC_RIGHT_STICK_ANTI_DEADZONE_PREF_STRING,
            PreferenceConfiguration.OSC_RIGHT_STICK_EDGE_HOLD_PREF_STRING,
            PreferenceConfiguration.OSC_RIGHT_STICK_SLOW_BOOST_PREF_STRING,
            PreferenceConfiguration.OSC_STICK_ANTI_DEADZONE_PREF_STRING,
    };

    public static class Profile {
        public final String id;
        public final String name;

        Profile(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final Context context;
    private final SharedPreferences store;

    public OscProfiles(Context context) {
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        this.store = this.context.getSharedPreferences(PROFILES_PREFERENCE, Context.MODE_PRIVATE);
        ensureInitialized();
    }

    /** The first time, wrap the settings and layout the user already has into one profile. */
    private void ensureInitialized() {
        if (store.contains(ACTIVE_KEY)) {
            return;
        }
        String id = newId();
        JSONArray order = new JSONArray();
        order.put(id);
        store.edit()
                .putString(ORDER_KEY, order.toString())
                .putString(ACTIVE_KEY, id)
                .putString(NAME_PREFIX + id, context.getString(R.string.osc_profile_default_name))
                .putString(DATA_PREFIX + id, captureLive().toString())
                .commit();
    }

    public List<Profile> getProfiles() {
        List<Profile> profiles = new ArrayList<>();
        for (String id : getOrder()) {
            profiles.add(new Profile(id, getName(id)));
        }
        return profiles;
    }

    public String getActiveId() {
        return store.getString(ACTIVE_KEY, null);
    }

    public String getActiveName() {
        return getName(getActiveId());
    }

    public String getName(String id) {
        return store.getString(NAME_PREFIX + id, "");
    }

    /** Make another profile active and write its settings and layout into the live keys. */
    public void switchTo(String id) {
        String activeId = getActiveId();
        if (id.equals(activeId) || !getOrder().contains(id)) {
            return;
        }

        saveActive();

        JSONObject data;
        try {
            data = new JSONObject(store.getString(DATA_PREFIX + id, "{}"));
        } catch (JSONException e) {
            e.printStackTrace();
            data = new JSONObject();
        }
        applyLive(data);
        store.edit().putString(ACTIVE_KEY, id).commit();
    }

    /** Copy the active profile under a new name and make the copy active. */
    public String createFromActive(String name) {
        saveActive();
        return addProfile(name, store.getString(DATA_PREFIX + getActiveId(), "{}"), true);
    }

    /** Copy any profile under a new name, leaving the active one as it is. */
    public String duplicate(String id, String name) {
        saveActive();
        return addProfile(name, store.getString(DATA_PREFIX + id, "{}"), false);
    }

    public void rename(String id, String name) {
        if (getOrder().contains(id)) {
            store.edit().putString(NAME_PREFIX + id, name).commit();
        }
    }

    /** Delete a profile. The last one is kept; deleting the active one switches to its neighbour. */
    public boolean delete(String id) {
        List<String> order = getOrder();
        int index = order.indexOf(id);
        if (index < 0 || order.size() <= 1) {
            return false;
        }
        if (id.equals(getActiveId())) {
            switchTo(order.get(index > 0 ? index - 1 : 1));
        }
        order.remove(index);
        store.edit()
                .putString(ORDER_KEY, new JSONArray(order).toString())
                .remove(NAME_PREFIX + id)
                .remove(DATA_PREFIX + id)
                .commit();
        return true;
    }

    /** Store the live settings and layout as the active profile's copy. */
    public void saveActive() {
        String activeId = getActiveId();
        if (activeId != null) {
            store.edit().putString(DATA_PREFIX + activeId, captureLive().toString()).commit();
        }
    }

    /** A name like "Profile 2" that is not taken yet. */
    public String suggestNewName() {
        List<Profile> profiles = getProfiles();
        for (int n = profiles.size() + 1; ; n++) {
            String name = context.getString(R.string.osc_profile_numbered_name, n);
            boolean taken = false;
            for (Profile profile : profiles) {
                if (profile.name.equals(name)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return name;
            }
        }
    }

    private String addProfile(String name, String data, boolean makeActive) {
        String id = newId();
        List<String> order = getOrder();
        order.add(id);
        SharedPreferences.Editor editor = store.edit()
                .putString(ORDER_KEY, new JSONArray(order).toString())
                .putString(NAME_PREFIX + id, name)
                .putString(DATA_PREFIX + id, data);
        if (makeActive) {
            editor.putString(ACTIVE_KEY, id);
        }
        editor.commit();
        return id;
    }

    private String newId() {
        int next = store.getInt(NEXT_ID_KEY, 1);
        store.edit().putInt(NEXT_ID_KEY, next + 1).commit();
        return "p" + next;
    }

    private List<String> getOrder() {
        List<String> order = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(store.getString(ORDER_KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                order.add(array.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return order;
    }

    private SharedPreferences settingsPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private SharedPreferences layoutPrefs() {
        return context.getSharedPreferences(VirtualControllerConfigurationLoader.OSC_PREFERENCE, Context.MODE_PRIVATE);
    }

    private JSONObject captureLive() {
        JSONObject data = new JSONObject();
        try {
            JSONObject settings = new JSONObject();
            Map<String, ?> allSettings = settingsPrefs().getAll();
            for (String key : PROFILE_SETTING_KEYS) {
                Object value = allSettings.get(key);
                // A key that was never set stays unset, so the profile keeps following its default
                if (value != null) {
                    settings.put(key, value);
                }
            }
            data.put(DATA_SETTINGS, settings);

            // The layout file holds button positions and the gamepad/mouse choice
            JSONObject layout = new JSONObject();
            for (Map.Entry<String, ?> entry : layoutPrefs().getAll().entrySet()) {
                if (entry.getValue() != null) {
                    layout.put(entry.getKey(), entry.getValue());
                }
            }
            data.put(DATA_LAYOUT, layout);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return data;
    }

    private void applyLive(JSONObject data) {
        JSONObject settings = data.optJSONObject(DATA_SETTINGS);
        SharedPreferences.Editor settingsEditor = settingsPrefs().edit();
        for (String key : PROFILE_SETTING_KEYS) {
            Object value = settings != null ? settings.opt(key) : null;
            if (value == null) {
                settingsEditor.remove(key);
            } else {
                putValue(settingsEditor, key, value);
            }
        }
        settingsEditor.commit();

        JSONObject layout = data.optJSONObject(DATA_LAYOUT);
        SharedPreferences.Editor layoutEditor = layoutPrefs().edit().clear();
        if (layout != null) {
            Iterator<String> keys = layout.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                putValue(layoutEditor, key, layout.opt(key));
            }
        }
        layoutEditor.commit();
    }

    private static void putValue(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Number) {
            editor.putFloat(key, ((Number) value).floatValue());
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        }
    }
}
