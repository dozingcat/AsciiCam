package com.dozingcatsoftware.util;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import android.graphics.Bitmap;
import android.hardware.Camera;

/** This class contains useful methods for working with the camera in Android apps. The methods will build and run 
 * under Android 1.6 or later; methods available only in later versions are called using reflection.
 */

public class CameraUtils {
	
	/** Returns the number of cameras accessible to the Android API. This is always 1 on platforms earlier than Android 2.3,
	 * and on 2.3 or later it is the result of Camera.getNumberOfCameras().
	 */
	public static int numberOfCameras() {
		try {
			Method m = Camera.class.getMethod("getNumberOfCameras");
			return ((Number)m.invoke(null)).intValue();
		}
		catch(Exception ex) {
			return 1;
		}
	}

	/** Returns a list of available camera preview sizes, or null if the Android API to get the sizes is not available.
	 */
	public static List<Camera.Size> previewSizesForCameraParameters(Camera.Parameters params) {
    	try {
    		Method m = params.getClass().getMethod("getSupportedPreviewSizes");
    		return (List<Camera.Size>)m.invoke(params);
    	}
    	catch(Exception ex) {
    		return null;
    	}
	}

	/** Attempts to find the camera preview size as close as possible to the given width and height. If the Android API
	 * does not support retrieving available camera preview sizes, this method returns null. Otherwise, returns the
	 * camera preview size that minimizes the sum of the differences between the actual and requested height and width.
	 */
	public static Camera.Size bestCameraSizeForWidthAndHeight(Camera.Parameters params, int width, int height) {
		List<Camera.Size> previewSizes = previewSizesForCameraParameters(params);
		if (previewSizes==null || previewSizes.size()==0) return null;
		
    	Camera.Size bestSize = null;
    	int bestDiff = 0;
    	// find the preview size that minimizes the difference between width and height
		for(Camera.Size size : previewSizes) {
			int diff = Math.abs(size.width - width) + Math.abs(size.height - height);
			if (bestSize==null || diff<bestDiff) {
				bestSize = size;
				bestDiff = diff;
			}
		}
		return bestSize;
	}
	
	/** Updates the Camera object's preview size to the nearest match for the given width and height.
	 * Returns the preview size whether it was updated or not.
	 */
	public static Camera.Size setNearestCameraPreviewSize(Camera camera, int width, int height) {
		Camera.Parameters params = camera.getParameters();
		Camera.Size size = bestCameraSizeForWidthAndHeight(params, width, height);
		if (size!=null) {
			params.setPreviewSize(size.width, size.height);
			camera.setParameters(params);
		}
		return params.getPreviewSize();
	}
	
	/** Returns a list of available camera picture sizes, or null if the Android API to get the sizes is not available.
	 */
	public static List<Camera.Size> pictureSizesForCameraParameters(Camera.Parameters params) {
    	try {
    		Method m = params.getClass().getMethod("getSupportedPictureSizes");
    		return (List<Camera.Size>)m.invoke(params);
    	}
    	catch(Exception ex) {
    		return null;
    	}
	}
	
	/** Sets the camera's picture size to the maximum available size, as determined by number of pixels (width*height).
	 * Returns a Camera.Size object containing the updated picture size.
	 */
	public static Camera.Size setLargestCameraSize(Camera camera) {
		Camera.Parameters params = camera.getParameters();
		List<Camera.Size> pictureSizes = pictureSizesForCameraParameters(params);
		if (pictureSizes!=null && pictureSizes.size()>0) {
			long bestPixels = -1;
			Camera.Size bestSize = null;
			for(Camera.Size size : pictureSizes) {
				long pixels = size.width * size.height;
				if (pixels>bestPixels || bestPixels<0) {
					bestPixels = pixels;
					bestSize = size;
				}
			}
			if (bestSize!=null) {
				params.setPictureSize(bestSize.width, bestSize.height);
				camera.setParameters(params);
			}
		}
		
		return params.getPictureSize();
	}
	
	/** Sets pixels in the given bitmap to a grayscale image from the byte array from a camera preview,
	 * assumed to be in YUV (NV21) format with brightness pixels first.
	 */
	public static Bitmap fillGrayscaleBitmapFromCameraData(Bitmap bitmap, byte[] cdata, int width, int height) {
		int[] pixels = new int[cdata.length];
		for(int i=0; i<cdata.length; i++) {
			int g = 0xff & cdata[i];
			pixels[i] = (255<<24) + (g<<16) + (g<<8) + g;
		}
		bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
		return bitmap;
	}

	/** Opens the camera with the given ID. If the Android API doesn't support multiple cameras (i.e. prior to Android 2.3), 
	 * always opens the primary camera.
	 */
	public static Camera openCamera(int cameraId) {
		if (cameraId>0) {
			Method openMethod = null;
			try {
				openMethod = Camera.class.getMethod("open", int.class);
			}
			catch(Exception ex) {
				openMethod = null;
			}
			if (openMethod!=null) {
				try {
					return (Camera)openMethod.invoke(null, cameraId);
				}
				catch(Exception ignored) {}
			}
		}
		return Camera.open();
	}
	
	static Class BYTE_ARRAY_CLASS = (new byte[0]).getClass();
	static Method addPreviewBufferMethod;
	static Method setPreviewCallbackWithBufferMethod;
	
	static {
		try {
			addPreviewBufferMethod = Camera.class.getMethod("addCallbackBuffer", BYTE_ARRAY_CLASS);
			setPreviewCallbackWithBufferMethod = Camera.class.getMethod("setPreviewCallbackWithBuffer", Camera.PreviewCallback.class);
		}
		catch(Exception notFound) {
			addPreviewBufferMethod = setPreviewCallbackWithBufferMethod = null;
		}
	}
	
	/** Returns true if the Camera.addCallbackBuffer API is available (i.e. Android 2.2 or later).
	 */
	public static boolean previewBuffersSupported() {
		return addPreviewBufferMethod!=null;
	}
	
	/** Attempts to allocate and register the given number of preview callback buffers. Uses the camera's current preview
	 * size to determine the size of the buffers. If the Android API doesn't support preview buffers, does nothing.
	 * Returns true if successful.
	 */
	public static boolean createPreviewCallbackBuffers(Camera camera, int nbuffers) {
		if (addPreviewBufferMethod==null) return false;
		
		Camera.Size previewSize = camera.getParameters().getPreviewSize();
		// 12 bits per pixel for preview buffer (8 chroma bytes, then average of 2 bytes each for Cr and Cb)
		int bufferSize = previewSize.width * previewSize.height * 3 / 2;
		for(int i=0; i<nbuffers; i++) {
			byte[] buffer = new byte[bufferSize];
			try {
				addPreviewBufferMethod.invoke(camera, buffer);
			}
			catch(Exception ignored) {
				return false;
			}
		}
		return true;
	}
	
	/** Attempts to add the given byte array as a camera preview callback buffer. If the Android API doesn't support preview buffers,
	 * does nothing and returns false. Returns true if successful.
	 */
	public static boolean addPreviewCallbackBuffer(Camera camera, byte[] buffer) {
		if (addPreviewBufferMethod==null) return false;
		try {
			addPreviewBufferMethod.invoke(camera, buffer);
			return true;
		}
		catch(Exception ignored) {
			return false;
		}
	}
	
	/** Sets the given callback object on the given camera. Calls setPreviewCallbackWithBuffer if the Android API supports it,
	 * otherwise calls setPreviewCallback.
	 */
	public static boolean setPreviewCallbackWithBuffer(Camera camera, Camera.PreviewCallback callback) {
		if (setPreviewCallbackWithBufferMethod==null) {
			camera.setPreviewCallback(callback);
			return false;
		}
		try {
			setPreviewCallbackWithBufferMethod.invoke(camera, callback);
			return true;
		}
		catch(Exception ignored) {
			camera.setPreviewCallback(callback);
			return false;
		}
	}

	/** Returns a list of available camera flash modes. If the Android API doesn't support getting flash modes (requires 2.0 or later),
	 * returns a list with a single element of "off", corresponding to Camera.Parameters.FLASH_MODE_OFF.
	 */
	public static List<String> getFlashModes(Camera camera) {
		Camera.Parameters params = camera.getParameters();
		try {
			Method flashModesMethod = params.getClass().getMethod("getSupportedFlashModes");
			List<String> result = (List<String>)flashModesMethod.invoke(params);
			if (result!=null) return result;
		}
		catch(Exception ignored) {}
		return Collections.singletonList("off");
	}

	/** Attempts to set the camera's flash mode. Returns true if successful, false if the Android API doesn't support setting flash modes.
	 */
	public static boolean setFlashMode(Camera camera, String mode) {
		Camera.Parameters params = camera.getParameters();
		try {
			Method flashModeMethod = params.getClass().getMethod("setFlashMode", String.class);
			flashModeMethod.invoke(params, mode);
			camera.setParameters(params);
			return true;
		}
		catch(Exception ignored) {
			return false;
		}
	}
	
	/** Returns true if the camera supports flash. */
	public static boolean cameraSupportsFlash(Camera camera) {
		return getFlashModes(camera).contains("on");
	}
	
	/** Returns true if the camera supports auto-flash mode. */
	public static boolean cameraSupportsAutoFlash(Camera camera) {
		return getFlashModes(camera).contains("auto");
	}
}
