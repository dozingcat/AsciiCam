// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.dozingcatsoftware.util.ARManager;
import com.dozingcatsoftware.util.CameraUtils;
import com.dozingcatsoftware.asciicam.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;

public class AsciiCamActivity extends Activity implements PreviewCallback {
    ARManager arManager;
    AsciiConverter asciiConverter = new AsciiConverter();
    AsciiConverter.Result asciiResult = new AsciiConverter.Result();
    
    AsciiConverter.ColorType colorType = AsciiConverter.ColorType.ANSI_COLOR;
    String pixelChars;
    
    Object pictureLock = new Object();
    final static int ACTIVITY_PREFERENCES = 1;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        
        cameraView = (SurfaceView)findViewById(R.id.cameraView);
        overlayView = (OverlayView)findViewById(R.id.overlayView);
        
        arManager = ARManager.createAndSetupCameraView(this, cameraView, this);
        arManager.setPreferredPreviewSize(640,400);
        arManager.setNumberOfPreviewCallbackBuffers(1);

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                handleMainViewTouch(event);
                return true;
            }
        });
        
        this.findViewById(R.id.switchCameraButton).setVisibility(CameraUtils.numberOfCameras() > 1 ? View.VISIBLE : View.GONE);
        
        updateFromPreferences();
    }
    
    SurfaceView cameraView;
    OverlayView overlayView;
    boolean cameraViewReady = false;
    boolean appVisible = false;

    @Override
    public void onPause() {
        appVisible = false;
        arManager.stopCamera();
        asciiConverter.destroyThreadPool();
        super.onPause();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        appVisible = true;
        arManager.startCameraIfVisible();
    }

    public void handleMainViewTouch(MotionEvent event) {
        if (event.getAction()==MotionEvent.ACTION_DOWN) {
        	AsciiConverter.ColorType[] colorTypeValues = AsciiConverter.ColorType.values();
        	this.colorType = colorTypeValues[(this.colorType.ordinal() + 1) % colorTypeValues.length];
            saveColorStyleToPreferences();
        }
    }
    
    void updateFromPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        pixelChars = prefs.getString(getString(R.string.pixelCharsPrefId), null);
        
        String colorTypeName = prefs.getString("colorType", null);
        if (colorTypeName!=null) {
        	try {
        		this.colorType = AsciiConverter.ColorType.valueOf(colorTypeName);;
        	}
        	catch(Exception ignored) {}
        }
        if (colorType==null) {
        	colorType = AsciiConverter.ColorType.ANSI_COLOR;
        }
    }
    
    void saveColorStyleToPreferences() {
    	SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit();
    	editor.putString("colorType", colorType.name());
    	editor.commit();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) { 
        super.onActivityResult(requestCode, resultCode, intent); 

        switch(requestCode) { 
            case ACTIVITY_PREFERENCES:
                updateFromPreferences();
                break;
        }
    }

    public void onClick_takePicture(View view) {
        try {
            String datestr = FILENAME_DATE_FORMAT.format(new Date());
            String dir = BASE_PICTURE_DIR + File.separator + datestr;
            (new File(dir)).mkdirs();
            if (!((new File(dir)).isDirectory())) {
                return;
            }
            String htmlPath = saveHTML(dir, datestr);
            String pngPath = savePNG(dir, datestr);
            String thumbnailPath = saveThumbnail(BASE_PICTURE_DIR + File.separator + "thumbnails", datestr);
            
            ViewImageActivity.startActivityWithImageURI(this, Uri.fromFile(new File(pngPath)), "image/png");
        }
        catch(IOException ex) {
            Log.e("AsciiCam", "Error saving picture", ex);
        }
    }
    
    public void onClick_gotoGallery(View view) {
        Intent intent = LibraryActivity.intentWithImageDirectory(this, BASE_PICTURE_DIR);
        startActivity(intent);
    }
    
    public void onClick_gotoAbout(View view) {
        AboutActivity.startIntent(this);
    }
    
    public void onClick_gotoPreferences(View view) {
        AsciiCamPreferences.startIntent(this, ACTIVITY_PREFERENCES);
    }
    
    public void onClick_switchCamera(View view) {
        arManager.switchToNextCamera();
    }
    
    static DateFormat FILENAME_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    static String BASE_PICTURE_DIR = Environment.getExternalStorageDirectory() + File.separator + "AsciiCam";
    
    String saveHTML(String dir, String imageName) throws IOException {
        String outputFilePath;
        FileWriter output = null;
        try {
            output = new FileWriter(dir + File.separator + imageName + ".txt");
            output.write(asciiResult.getAsciiString(true));
            output.close();
            
            outputFilePath = dir + File.separator + imageName + ".html";
            output = new FileWriter(outputFilePath);
            output.write("<html><head></title>Ascii Picture " + imageName + "</title></head>");
            output.write("<body bgcolor=#000000>\n");

            output.write("<pre>");
            for(int r=0; r<asciiResult.rows; r++) {
                int lastColor = -1;
                // loop precondition: output is in the middle of a <span> tag.
                // This allows skipping the tag if it's a space or the same color as previous char.
                output.write("<span>");
                for(int c=0; c<asciiResult.columns; c++) {
                    String asciiChar = asciiResult.stringAtRowColumn(r, c);
                    // don't use span tag for space
                    if (" ".equals(asciiChar)) {
                        output.write(asciiChar);
                        continue;
                    }
                    int color;
                    if (asciiResult.hasColor()) {
                        color = asciiResult.colorAtRowColumn(r, c);
                    }
                    else {
                        color = 0xffffffff;
                    }
                    if (color==lastColor) {
                        output.write(asciiChar);
                        continue;
                    }
                    String htmlColor = Integer.toHexString(color & 0x00ffffff);
                    while (htmlColor.length() < 6) {
                        htmlColor = "0" + htmlColor;
                    }
                    lastColor = color;
                    output.write(String.format("</span><span style=\"color:%s\">%s", htmlColor, asciiChar));
                }
                output.write("</span>\n");
            }
            output.write("</pre>\n");
            output.write("</body></html>");
            output.close();
            
        }
        finally {
            if (output!=null) output.close();
        }
        return outputFilePath;
    }
    
    String savePNG(String dir, String imageName) throws IOException {
        Bitmap bitmap = overlayView.drawIntoNewBitmap();
        return saveBitmap(bitmap, dir, imageName);
    }
    
    String saveThumbnail(String dir, String imageName) throws IOException {
        Bitmap bitmap = overlayView.drawIntoThumbnailBitmap();
        return saveBitmap(bitmap, dir, imageName);
    }
    
    String saveBitmap(Bitmap bitmap, String dir, String imageName) throws IOException {
        String outputFilePath;
        FileOutputStream output = null;
        try {
            outputFilePath = dir + File.separator + imageName + ".png";
            output = new FileOutputStream(outputFilePath);
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, output);
            output.close();
        }
        finally {
            if (output!=null) output.close();
        }
        return outputFilePath;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Camera.Size size = camera.getParameters().getPreviewSize();
        overlayView.setCameraPreviewSize(size.width, size.height);
        synchronized(pictureLock) {
            asciiConverter.computeResultForCameraData(data, size.width, size.height, 
                    overlayView.asciiRows(), overlayView.asciiColumns(), colorType, pixelChars, asciiResult);
        }
        
        overlayView.setAsciiConverterResult(asciiResult);
        overlayView.postInvalidate();
        
        CameraUtils.addPreviewCallbackBuffer(camera, data);
    }
}