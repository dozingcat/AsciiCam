package com.dozingcatsoftware.util;

import java.io.FileNotFoundException;
import java.lang.reflect.Method;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.view.View;

public class AndroidUtils {

	/** Adds a click listener to the given view which invokes the method named by methodName on the given target.
	 * The method must be public and take no arguments.
	 */
	public static void bindOnClickListener(final Object target, View view, String methodName) {
		final Method method;
		try {
			method = target.getClass().getMethod(methodName);
		}
		catch(Exception ex) {
			throw new IllegalArgumentException(ex);
		}
		view.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				try {
					method.invoke(target);
				}
				catch(Exception ex) {
					throw new RuntimeException(ex);
				}
			}
		});
	}

	public static interface MediaScannerCallback {
		public void mediaScannerCompleted(String scanPath, Uri scanURI);
	}

    /** Notifies the OS to index the new image, so it shows up in Gallery. Allows optional callback method to notify client when
     * the scan is completed, e.g. so it can access the "content" URI that gets assigned.
     */
    public static void scanSavedMediaFile(final Context context, final String path, final MediaScannerCallback callback) {
    	// silly array hack so closure can reference scannerConnection[0] before it's created 
    	final MediaScannerConnection[] scannerConnection = new MediaScannerConnection[1];
		try {
			MediaScannerConnection.MediaScannerConnectionClient scannerClient = new MediaScannerConnection.MediaScannerConnectionClient() {
				public void onMediaScannerConnected() {
					scannerConnection[0].scanFile(path, null);
				}

				public void onScanCompleted(String scanPath, Uri scanURI) {
					scannerConnection[0].disconnect();
					if (callback!=null) {
						callback.mediaScannerCompleted(scanPath, scanURI);
					}
				}
			};
    		scannerConnection[0] = new MediaScannerConnection(context, scannerClient);
    		scannerConnection[0].connect();
		}
		catch(Exception ignored) {}
    }
    
    public static void scanSavedMediaFile(final Context context, final String path) {
    	scanSavedMediaFile(context, path, null);
    }
    
	/** Returns a BitmapFactory.Options object containing the size of the image at the given URI,
	 * without actually loading the image.
	 */
	public static BitmapFactory.Options computeBitmapSizeFromURI(Context context, Uri imageURI) throws FileNotFoundException {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(context.getContentResolver().openInputStream(imageURI), null, options);
		return options;
	}
	
	/** Returns a Bitmap from the given URI that may be scaled by an integer factor to reduce its size,
	 * while staying as least as large as the width and height parameters.
	 */
	public static Bitmap scaledBitmapFromURIWithMinimumSize(Context context, Uri imageURI, int width, int height) throws FileNotFoundException {
		BitmapFactory.Options options = computeBitmapSizeFromURI(context, imageURI);
		options.inJustDecodeBounds = false;
		
		float wratio = 1.0f*options.outWidth / width;
		float hratio = 1.0f*options.outHeight / height;		
		options.inSampleSize = (int)Math.min(wratio, hratio);
		
		return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(imageURI), null, options);		
	}
	
	/** Given a width and height, fills output array with scaled width and height values 
	 * such that one of the values is exactly equal to the given maximum width or height, 
	 * and the other value is less than or equal to the maximum.
	 */
	public static void getScaledWidthAndHeightToMaximum(
			int width, int height, int maxWidth, int maxHeight, int[] output) {
		output[0] = width;
		output[1] = height;
		// common cases: if one dimension fits exactly and the other is smaller, return unmodified
		if (width==maxWidth && height<=maxHeight) return;
		if (height==maxHeight && width<=maxWidth) return;
		float wratio = ((float)width)/maxWidth;
		float hratio = ((float)height)/maxHeight;
		if (wratio<=hratio) {
			// scale to full height, partial width
			output[0] = (int)(width/hratio);
			output[1] = maxHeight;
		}
		else {
			// scale to full width, partial height
			output[0] = maxWidth;
			output[1] = (int)(height/wratio);
		}
	}
	
	public static int[] scaledWidthAndHeightToMaximum(int width, int height, int maxWidth, int maxHeight) {
		int[] output = new int[2];
		getScaledWidthAndHeightToMaximum(width, height, maxWidth, maxHeight, output);
		return output;
	}
}
