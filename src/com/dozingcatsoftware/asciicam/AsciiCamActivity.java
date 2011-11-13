package com.dozingcatsoftware.asciicam;

import com.dozingcatsoftware.util.ARManager;
import com.dozingcatsoftware.util.CameraUtils;
import com.dozingcatsoftware.asciicam.R;

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;

public class AsciiCamActivity extends Activity implements PreviewCallback {
	ARManager arManager;
	AsciiConverter asciiConverter = new AsciiConverter();
	AsciiConverter.Result asciiResult = new AsciiConverter.Result();
	boolean useColor = false;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	setContentView(R.layout.main);
    	
    	cameraView = (SurfaceView)findViewById(R.id.cameraView);
    	overlayView = (OverlayView)findViewById(R.id.overlayView);
    	
    	arManager = ARManager.createAndSetupCameraView(this, cameraView, this);
    	arManager.setPreferredPreviewSize(400, 240);
    	arManager.setNumberOfPreviewCallbackBuffers(1);

    	overlayView.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				handleMainViewTouch(event);
				return true;
			}
    	});
    }
    
    SurfaceView cameraView;
    OverlayView overlayView;
    boolean cameraViewReady = false;
    boolean appVisible = false;

    @Override
    public void onPause() {
    	appVisible = false;
    	arManager.stopCamera();
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
    		useColor = !useColor;
    	}
    }

    @Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		Camera.Size size = camera.getParameters().getPreviewSize();
		asciiConverter.computeResultForCameraData(data, size.width, size.height, 
				overlayView.asciiRows(), overlayView.asciiColumns(), useColor, asciiResult);
		
		overlayView.setAsciiConverterResult(asciiResult);
		overlayView.postInvalidate();
		
    	CameraUtils.addPreviewCallbackBuffer(camera, data);
		
	}
}