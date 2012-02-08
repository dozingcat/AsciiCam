package com.dozingcatsoftware.asciicam;

import com.dozingcatsoftware.asciicam.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class AsciiCamPreferences extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    // sets FLAG_ACTIVITY_NO_HISTORY so exiting and relaunching won't go back to this screen
    public static Intent startIntent(Activity parent, int requestCode) {
        Intent aboutIntent = new Intent(parent, AsciiCamPreferences.class);
        aboutIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        parent.startActivityForResult(aboutIntent, requestCode);
        return aboutIntent;
    }
}
