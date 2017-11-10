// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

import com.dozingcatsoftware.asciicam.R;

public class AboutActivity extends Activity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.about);
	}

	// sets FLAG_ACTIVITY_NO_HISTORY so exiting and relaunching won't go back to help screen
	public static Intent startIntent(Context parent) {
    	Intent aboutIntent = new Intent(parent, AboutActivity.class);
    	aboutIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    	parent.startActivity(aboutIntent);
    	return aboutIntent;
	}

}
