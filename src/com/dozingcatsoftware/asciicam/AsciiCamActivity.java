// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

import com.dozingcatsoftware.util.ARManager;
import com.dozingcatsoftware.util.AndroidUtils;
import com.dozingcatsoftware.util.CameraUtils;
import com.dozingcatsoftware.util.ShutterButton;
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
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;

public class AsciiCamActivity extends Activity implements PreviewCallback, ShutterButton.OnShutterButtonListener {
    ARManager arManager;
    AsciiConverter asciiConverter = new AsciiConverter();
    AsciiConverter.Result asciiResult = new AsciiConverter.Result();
    
    AsciiConverter.ColorType colorType = AsciiConverter.ColorType.ANSI_COLOR;
    Map<AsciiConverter.ColorType, String> pixelCharsMap = new EnumMap<AsciiConverter.ColorType, String>(AsciiConverter.ColorType.class);
    
    Object pictureLock = new Object();
    final static int ACTIVITY_PREFERENCES = 1;
    
    ImageButton cycleColorButton;
    ShutterButton shutterButton;
    
    Handler handler = new Handler();

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        
        cameraView = (SurfaceView)findViewById(R.id.cameraView);
        overlayView = (OverlayView)findViewById(R.id.overlayView);
        cycleColorButton = (ImageButton)findViewById(R.id.cycleColorButton);
        shutterButton = (ShutterButton)findViewById(R.id.shutterButton);
        shutterButton.setOnShutterButtonListener(this);
        
        arManager = ARManager.createAndSetupCameraView(this, cameraView, this);
        arManager.setPreferredPreviewSize(640,400);
        arManager.setNumberOfPreviewCallbackBuffers(1);

        findViewById(R.id.switchCameraButton).setVisibility(CameraUtils.numberOfCameras() > 1 ? View.VISIBLE : View.GONE);
        updateFromPreferences();
        updateColorButton();
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
        AndroidUtils.setSystemUiLowProfile(cameraView);
    }

    void updateFromPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        for(AsciiConverter.ColorType colorType : AsciiConverter.ColorType.values()) {
        	String prefsKey = getString(R.string.pixelCharsPrefIdPrefix) + colorType.name();
        	pixelCharsMap.put(colorType, prefs.getString(prefsKey, ""));
        }
        
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
    
    static String thumbnailDirectory() {
    	return BASE_PICTURE_DIR + File.separator + "thumbnails";
    }

    void takePicture() {
        try {
            String datestr = FILENAME_DATE_FORMAT.format(new Date());
            String dir = BASE_PICTURE_DIR + File.separator + datestr;
            // make sure image and thumbnail directories exist
            (new File(dir)).mkdirs();
            if (!((new File(dir)).isDirectory())) {
                return;
            }
            String thumbnailDir = thumbnailDirectory();
            (new File(thumbnailDir)).mkdirs();
            // create .noindex file so thumbnail pictures won't be indexed and show up in the gallery app
            (new File(thumbnailDir + File.separator + ".nomedia")).createNewFile();

            String htmlPath = saveHTML(dir, datestr);
            String pngPath = savePNG(dir, datestr);
            String thumbnailPath = saveThumbnail(thumbnailDirectory(), datestr);
            
            ViewImageActivity.startActivityWithImageURI(this, Uri.fromFile(new File(pngPath)), "image/png");
        }
        catch(IOException ex) {
            Log.e("AsciiCam", "Error saving picture", ex);
        }
    }

    void updateColorButton() {
    	try {
    		String resName = "btn_color_" + this.colorType.name().toLowerCase();
    		Integer resId = (Integer)R.drawable.class.getField(resName).get(null);
    		cycleColorButton.setImageResource(resId);
    	}
    	catch(Exception ex) {
    		Log.e("AsciiCam", "Error updating color button", ex);
    	}
    }
    
    // onClick_ methods are assigned as onclick handlers in the main.xml layout
    public void onClick_cycleColorMode(View view) {
    	AsciiConverter.ColorType[] colorTypeValues = AsciiConverter.ColorType.values();
    	this.colorType = colorTypeValues[(this.colorType.ordinal() + 1) % colorTypeValues.length];
        saveColorStyleToPreferences();
        updateColorButton();
    }
    
    public void onClick_gotoGallery(View view) {
        Intent intent = LibraryActivity.intentWithImageDirectory(this, BASE_PICTURE_DIR, thumbnailDirectory());
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
        	outputFilePath = dir + File.separator + imageName + ".html";
            output = new FileWriter(outputFilePath);
            output.write("<html><head></title>Ascii Picture " + imageName + "</title></head>");
            output.write("<body><div style=\"background: black; letter-spacing: 3px;\">\n");

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
                    int color = asciiResult.colorAtRowColumn(r, c);
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
            output.write("</div></body></html>");
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
                    overlayView.asciiRows(), overlayView.asciiColumns(), 
                    colorType, pixelCharsMap.get(colorType), asciiResult);
        }
        
        overlayView.setAsciiConverterResult(asciiResult);
        overlayView.postInvalidate();
        
        CameraUtils.addPreviewCallbackBuffer(camera, data);
    }

	@Override
	public void onShutterButtonFocus(boolean pressed) {
		shutterButton.setImageResource(pressed ? R.drawable.btn_camera_shutter_pressed_holo : 
			                                     R.drawable.btn_camera_shutter_holo);
	}

	@Override
	public void onShutterButtonClick() {
		takePicture();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    // take picture when pushing hardware camera button or trackball center
	    if ((keyCode==KeyEvent.KEYCODE_CAMERA || keyCode==KeyEvent.KEYCODE_DPAD_CENTER) && event.getRepeatCount()==0) {
	        handler.post(new Runnable() {
	            public void run() {
	                takePicture();
	            }
	        });
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}
}