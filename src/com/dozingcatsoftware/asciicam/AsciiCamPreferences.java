// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class AsciiCamPreferences extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        Preference autoConvertPref = getPreferenceManager().findPreference(getString(R.string.autoConvertPicturesPrefId));
        autoConvertPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference pref, Object value) {
                // Update broadcast receivers immediately so the change takes effect even if the user
                // doesn't go back to the main activity.
                setAutoConvertEnabled(AsciiCamPreferences.this, Boolean.TRUE.equals(value));
                return true;
            }
        });
    }

    // sets FLAG_ACTIVITY_NO_HISTORY so exiting and relaunching won't go back to this screen
    public static Intent startIntent(Activity parent, int requestCode) {
        Intent aboutIntent = new Intent(parent, AsciiCamPreferences.class);
        aboutIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        parent.startActivityForResult(aboutIntent, requestCode);
        return aboutIntent;
    }

    /**
     * Sets whether pictures saved by the camera app (or other apps which broadcast the appropriate intent)
     * should automatically be converted to ascii via the NewPictureReceiver broadcast receiver.
     */
    public static void setAutoConvertEnabled(Context context, boolean enabled) {
        // For N and above, schedule or cancel a JobService.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (enabled) {
                NewPictureJob.scheduleJob(context);
            }
            else {
                NewPictureJob.cancelJob(context);
            }
        }
        else {
            boolean receiverEnabled = false;
            boolean legacyReceiverEnabled = false;
            if (enabled) {
                try {
                    // Android 4.0 and later have a Camera.ACTION_NEW_PICTURE constant, which camera apps send after
                    // taking a picture. The NewPictureReceiver class listens for this broadcast. Earlier Android
                    // versions send the undocumented com.android.camera.NEW_PICTURE. This determines which
                    // receiver to enable based on whether the ACTION_NEW_PICTURE field exists.
                    android.hardware.Camera.class.getField("ACTION_NEW_PICTURE");
                    receiverEnabled = true;
                }
                catch(Exception ex) {
                    legacyReceiverEnabled = true;
                }
            }
            PackageManager pm = context.getPackageManager();
            pm.setComponentEnabledSetting(new ComponentName(context, NewPictureReceiver.class),
                    receiverEnabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(new ComponentName(context, NewPictureReceiverLegacyBroadcast.class),
                    legacyReceiverEnabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
