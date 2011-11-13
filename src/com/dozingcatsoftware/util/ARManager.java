package com.dozingcatsoftware.util;

import android.app.Activity;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class ARManager implements SurfaceHolder.Callback {
	
	Activity activity;
	SurfaceView cameraView;
	Camera.PreviewCallback previewCallback;
	Runnable cameraOpenedCallback;
	
	Camera camera;
	boolean cameraViewReady = false;
    int cameraId = 0;

	int preferredPreviewWidth = 0, preferredPreviewHeight = 0;
	int numPreviewCallbackBuffers = 0;
	
	public ARManager(Activity _activity, SurfaceView _cameraView, Camera.PreviewCallback _previewCallback) {
		this.activity = _activity;
		this.cameraView = _cameraView;
		this.previewCallback = _previewCallback;
	}
	
	public void setupCameraView() {
    	cameraView.getHolder().addCallback(this);
    	cameraView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}
	
	public static ARManager createAndSetupCameraView(Activity _activity, SurfaceView _cameraView, Camera.PreviewCallback _previewCallback) {
		ARManager manager = new ARManager(_activity, _cameraView, _previewCallback);
		manager.setupCameraView();
		return manager;
	}
	
	public void setPreferredPreviewSize(int width, int height) {
		this.preferredPreviewWidth = width;
		this.preferredPreviewHeight = height;
	}
	
	public void setNumberOfPreviewCallbackBuffers(int n) {
		this.numPreviewCallbackBuffers = n;
	}
	
	public void setCameraOpenedCallback(Runnable callback) {
		cameraOpenedCallback = callback;
	}
	
	public boolean startCamera() {
		if (camera==null) {
			try {
	            camera = CameraUtils.openCamera(cameraId);
	            if (cameraOpenedCallback!=null) {
	            	cameraOpenedCallback.run();
	            }
	            camera.setPreviewDisplay(cameraView.getHolder());
	            if (preferredPreviewWidth>0 && preferredPreviewHeight>0) {
	            	CameraUtils.setNearestCameraPreviewSize(camera, preferredPreviewWidth, preferredPreviewHeight);
	            }

	            if (numPreviewCallbackBuffers > 0) {
	            	CameraUtils.createPreviewCallbackBuffers(camera, this.numPreviewCallbackBuffers);
	            	CameraUtils.setPreviewCallbackWithBuffer(camera, this.previewCallback);
	            }
	            else {
		            camera.setPreviewCallback(this.previewCallback);
	            }
	            camera.startPreview();
			}
			catch(Exception ex) {
				camera = null;
			}
		}
		return (camera!=null);
	}

	
	public void startCameraIfVisible() {
		if (cameraViewReady) {
			startCamera();
		}
	}
	
	public void stopCamera() {
		if (camera!=null) {
			camera.setPreviewCallback(null);
			camera.stopPreview();
	    	camera.release();
	    	camera = null;
		}
	}
	
    public void switchToCamera(int _cameraId) {
    	if (camera!=null) {
    		stopCamera();
    	}
    	this.cameraId = _cameraId;
    	startCameraIfVisible();
    }

    public void switchToNextCamera() {
    	switchToCamera((cameraId + 1) % CameraUtils.numberOfCameras());
    }
	
    // SurfaceHolder callbacks
	@Override 
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		this.cameraViewReady = true;
		startCameraIfVisible();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// all done in surfaceChanged
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		this.cameraViewReady = false;
		stopCamera();
	}

	
	public Camera getCamera() {
		return camera;
	}
	public int getCameraId() {
		return cameraId;
	}

}
