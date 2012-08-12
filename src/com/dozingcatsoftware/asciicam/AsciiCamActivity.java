// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.EnumMap;
import java.util.Map;

import com.dozingcatsoftware.util.ARManager;
import com.dozingcatsoftware.util.AndroidUtils;
import com.dozingcatsoftware.util.CameraPreviewProcessingQueue;
import com.dozingcatsoftware.util.CameraUtils;
import com.dozingcatsoftware.util.ShutterButton;
import com.dozingcatsoftware.asciicam.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.Toast;

public class AsciiCamActivity extends Activity 
        implements Camera.PreviewCallback, ShutterButton.OnShutterButtonListener, CameraPreviewProcessingQueue.Processor {
    
    ARManager arManager;
    AsciiConverter asciiConverter = new AsciiConverter();
    AsciiConverter.Result asciiResult = new AsciiConverter.Result();
    
    AsciiConverter.ColorType colorType = AsciiConverter.ColorType.ANSI_COLOR;
    Map<AsciiConverter.ColorType, String> pixelCharsMap = new EnumMap<AsciiConverter.ColorType, String>(AsciiConverter.ColorType.class);
    
    Object pictureLock = new Object();
    final static int ACTIVITY_PREFERENCES = 1;
    
    ImageButton cycleColorButton;
    ShutterButton shutterButton;
    SurfaceView cameraView;
    OverlayView overlayView;

    Handler handler = new Handler();
    boolean saveInProgress = false;
    boolean cameraViewReady = false;
    boolean appVisible = false;
    
    AsciiRenderer imageRenderer = new AsciiRenderer();
    AsciiImageWriter imageWriter = new AsciiImageWriter();
    
    CameraPreviewProcessingQueue imageProcessor = new CameraPreviewProcessingQueue();

    /** Called when the activity is first created. */
    @Override public void onCreate(Bundle savedInstanceState) {
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
    
    @Override public void onPause() {
        appVisible = false;
        arManager.stopCamera();
        asciiConverter.destroyThreadPool();
        imageProcessor.stop();
        super.onPause();
    }
    
    @Override public void onResume() {
        super.onResume();
        appVisible = true;
        arManager.startCameraIfVisible();
        imageProcessor.start(this);
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
    
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) { 
        super.onActivityResult(requestCode, resultCode, intent); 

        switch(requestCode) { 
            case ACTIVITY_PREFERENCES:
                updateFromPreferences();
                break;
        }
    }
    
    void takePictureThreadEntry(final AsciiConverter.Result result) {
        try {
            final String pngPath = imageWriter.saveImageAndThumbnail(imageRenderer.getVisibleBitmap(), 
                    imageRenderer.createThumbnailBitmap(result),
                    new AsciiImageWriter.HtmlProvider() {
                        public void writeHtml(Writer writer, String imageName) throws IOException {
                            imageRenderer.writeHtml(result, writer, imageName);
                        }
                    }
            );
            handler.post(new Runnable() {
               public void run() {
                   bitmapSaved(pngPath, "image/png");
               }
            });
        }
        catch(IOException ex) {
            Log.e("AsciiCam", "Error saving picture", ex);
        }
    }
    
    void takePicture() {
        if (saveInProgress) return;
        // use a separate thread to write the PNG and HTML files, so the UI doesn't block
        imageProcessor.pause();
        (new Thread() {
            public void run() {
                AsciiConverter.Result savePictureResult = null;
                synchronized(pictureLock) {
                    savePictureResult = asciiResult.copy();
                }
                try {
                    takePictureThreadEntry(savePictureResult);                    
                }
                finally {
                    imageProcessor.unpause();
                }
            }
        }).start();
    }
    
    void bitmapSaved(String path, String mimeType) {
        saveInProgress = false;
        if (!appVisible) return;
        if (path==null) {
            Toast.makeText(getApplicationContext(), getString(R.string.errorSavingPicture), Toast.LENGTH_SHORT).show();
        }
        else {
            ViewImageActivity.startActivityWithImageURI(this, Uri.fromFile(new File(path)), mimeType);
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
        Intent intent = LibraryActivity.intentWithImageDirectory(this,
                imageWriter.getBasePictureDirectory(), imageWriter.getThumbnailDirectory());
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
    

    @Override public void onPreviewFrame(byte[] data, Camera camera) {
        if (!saveInProgress) {
            Camera.Size size = camera.getParameters().getPreviewSize();
            imageProcessor.processImageData(data, size.width, size.height);
        }
        else {
            CameraUtils.addPreviewCallbackBuffer(camera, data);
        }
    }
    
    
    @Override public void processCameraImage(final byte[] data, final int width, final int height) {
        synchronized(pictureLock) {
            imageRenderer.setMaximumImageSize(overlayView.getWidth(), overlayView.getHeight());
            imageRenderer.setCameraImageSize(width, height);
            asciiConverter.computeResultForCameraData(data, width, height, 
                    imageRenderer.asciiRows(), imageRenderer.asciiColumns(), 
                    colorType, pixelCharsMap.get(colorType), asciiResult);
            overlayView.setBitmap(imageRenderer.createBitmap(asciiResult)); 
            overlayView.setFlipHorizontal(arManager.isCameraFrontFacing());
        }
        handler.post(new Runnable() {
           public void run() {
               CameraUtils.addPreviewCallbackBuffer(arManager.getCamera(), data);
               overlayView.invalidate();
           }
        });
    }
    
	@Override public void onShutterButtonFocus(boolean pressed) {
		shutterButton.setImageResource(pressed ? R.drawable.btn_camera_shutter_pressed_holo : 
			                                     R.drawable.btn_camera_shutter_holo);
	}

	@Override public void onShutterButtonClick() {
		takePicture();
	}

	@Override public boolean onKeyDown(int keyCode, KeyEvent event) {
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