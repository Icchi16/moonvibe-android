package com.limelight.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.R;

/** Dialogs for managing on-screen control profiles, shared by the stream menu and the settings. */
public class OscProfileDialogs {
    public interface NameCallback {
        void onName(String name);
    }

    /** Called after the profile in use changed, so its settings and layout can be loaded. */
    public interface ChangeCallback {
        void onChanged();
    }

    private OscProfileDialogs() {
    }

    public static void showNameDialog(Context context, int title, String initialName, final NameCallback callback) {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        input.setText(initialName);
        input.selectAll();

        FrameLayout container = new FrameLayout(context);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (!name.isEmpty()) {
                            callback.onName(name);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
    }

    /** Rename, duplicate or delete one profile. */
    public static void showProfileOptions(final Context context, final OscProfiles profiles,
                                          final OscProfiles.Profile profile,
                                          final ChangeCallback onListChanged,
                                          final ChangeCallback onActiveChanged) {
        String[] options = {
                context.getString(R.string.osc_profile_rename),
                context.getString(R.string.osc_profile_duplicate),
                context.getString(R.string.osc_profile_delete),
        };
        new AlertDialog.Builder(context)
                .setTitle(profile.name)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showNameDialog(context, R.string.osc_profile_rename_title, profile.name, new NameCallback() {
                                @Override
                                public void onName(String name) {
                                    profiles.rename(profile.id, name);
                                    onListChanged.onChanged();
                                }
                            });
                        } else if (which == 1) {
                            showNameDialog(context, R.string.osc_profile_new_title,
                                    context.getString(R.string.osc_profile_copy_name, profile.name), new NameCallback() {
                                @Override
                                public void onName(String name) {
                                    profiles.duplicate(profile.id, name);
                                    onListChanged.onChanged();
                                }
                            });
                        } else {
                            confirmDelete(context, profiles, profile, onListChanged, onActiveChanged);
                        }
                    }
                })
                .show();
    }

    private static void confirmDelete(final Context context, final OscProfiles profiles,
                                      final OscProfiles.Profile profile,
                                      final ChangeCallback onListChanged,
                                      final ChangeCallback onActiveChanged) {
        if (profiles.getProfiles().size() <= 1) {
            Toast.makeText(context, R.string.osc_profile_cannot_delete_last, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(context)
                .setMessage(context.getString(R.string.osc_profile_delete_confirm, profile.name))
                .setPositiveButton(R.string.osc_profile_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean wasActive = profile.id.equals(profiles.getActiveId());
                        if (profiles.delete(profile.id)) {
                            if (wasActive) {
                                onActiveChanged.onChanged();
                            } else {
                                onListChanged.onChanged();
                            }
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
