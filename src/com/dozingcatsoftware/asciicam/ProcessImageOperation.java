package com.dozingcatsoftware.asciicam;

import java.io.IOException;

import com.dozingcatsoftware.asciicam.AsciiConverter.ColorType;
import com.dozingcatsoftware.util.AndroidUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.WindowManager;

public class ProcessImageOperation {

    /**
     * Reads the image from the given URI, creates ASCII PNG and HTML files, and writes them to
     * a new directory under the AsciiCam directory in /sdcard. Returns the path to the PNG file.
     */
    public String processImage(Context context, Uri uri) throws IOException {
        // use current settings from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        ColorType colorType = ColorType.ANSI_COLOR;
        String colorTypeName = prefs.getString("colorType", null);
        if (colorTypeName!=null) {
            try {
                colorType = ColorType.valueOf(colorTypeName);;
            }
            catch(Exception ignored) {}
        }
        String prefsKey = context.getString(R.string.pixelCharsPrefIdPrefix) + colorType.name();
        String pixelChars = prefs.getString(prefsKey, null);

        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        // assume width is always larger
        int displayWidth = Math.max(display.getWidth(), display.getHeight());
        int displayHeight = Math.min(display.getWidth(), display.getHeight());

        final AsciiRenderer renderer = new AsciiRenderer();
        renderer.setMaximumImageSize(displayWidth, displayHeight);

        int minWidth = Math.max(2*renderer.asciiColumns(), 480);
        int minHeight = Math.max(2*renderer.asciiRows(), 320);
        Bitmap bitmap = AndroidUtils.scaledBitmapFromURIWithMinimumSize(context, uri, minWidth, minHeight);
        renderer.setCameraImageSize(bitmap.getWidth(), bitmap.getHeight());

        AsciiConverter converter = new AsciiConverter();
        final AsciiConverter.Result result = converter.computeResultForBitmap(bitmap,
                renderer.asciiRows(), renderer.asciiColumns(), colorType, pixelChars);

        AsciiImageWriter imageWriter = new AsciiImageWriter();
        String imagePath =  imageWriter.saveImageAndThumbnail(
                renderer.createBitmap(result), renderer.createThumbnailBitmap(result), result);
        AndroidUtils.scanSavedMediaFile(context, imagePath);
        return imagePath;
    }
}
