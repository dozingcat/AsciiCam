package com.dozingcatsoftware.asciicam;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;

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

    String processImage(Context context, Uri uri) throws IOException {
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
        
        Bitmap bitmap = AndroidUtils.scaledBitmapFromURIWithMinimumSize(context, uri, 480, 320);
        renderer.setCameraImageSize(bitmap.getWidth(), bitmap.getHeight());
        
        AsciiConverter converter = new AsciiConverter();
        final AsciiConverter.Result result = converter.computeResultForBitmap(bitmap, 
                renderer.asciiRows(), renderer.asciiColumns(), colorType, pixelChars);
        
        AsciiImageWriter imageWriter = new AsciiImageWriter();
        return imageWriter.saveImageAndThumbnail(renderer.createBitmap(result), 
                renderer.createThumbnailBitmap(result),
                new AsciiImageWriter.HtmlProvider() {
                    public void writeHtml(Writer writer, String imageName) throws IOException {
                        renderer.writeHtml(result, writer, imageName);
                        // TODO Auto-generated method stub
                        
                    }
                }
        );
    }
}