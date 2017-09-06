package com.dozingcatsoftware.asciicam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Receiver for the Camera.ACTION_NEW_PICTURE broadcast message sent when the camera app saves
 * a new picture. Calls ProcessImageOperation to create an ASCII version of the picture.
 */
public class NewPictureReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        // On Android N and later we use a JobService and shouldn't get this notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return;
        }
        //Log.i(getClass().getName(), "Got picture: " + intent.getData());
        try {
            (new ProcessImageOperation()).processImage(context, intent.getData());
            //Toast.makeText(context, "Saved ascii image: " + resultDir, Toast.LENGTH_LONG).show();
        }
        catch(Exception ex) {
            Log.e(getClass().getName(), "Error saving picture", ex);
            //Toast.makeText(context, "Error saving ascii image: " + ex, Toast.LENGTH_LONG).show();
        }
    }

}
