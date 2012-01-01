package com.dozingcatsoftware.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import com.dozingcatsoftware.util.AndroidUtils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;

/** This class implements a two-level cache for Bitmaps. The first level is an in-memory map
 * which uses SoftReferences so that the Bitmaps will be freed when necessary. The second
 * level is a location directory on the SD card, where smaller versions of the images
 * will be saved for faster retrieval. The exact location is determined by the ThumbnailLocator
 * implementation passed to the constructor.
 */

public class ScaledBitmapCache {
	
	public static interface ThumbnailLocator {
		File thumbnailFileForUri(Uri imageUri);
	}
	
	// Simple ThumbnailLocator for putting thumbnails into a single directory using the image URI's filename.
	static class FixedDirectoryLocator implements ThumbnailLocator {
		String thumbnailDirectory;
		
		public FixedDirectoryLocator(String thumbnailDirectory) {
			this.thumbnailDirectory = thumbnailDirectory;
		}
		public File thumbnailFileForUri(Uri imageUri) {
			String filename = imageUri.getLastPathSegment();
			return new File(thumbnailDirectory + File.separator + filename);
		}	
	}
	
	Context context;
	ThumbnailLocator thumbnailLocator;
	
	Map<Uri, SoftReference<Bitmap>> scaledBitmapCache = new HashMap<Uri, SoftReference<Bitmap>>();
	
	public ScaledBitmapCache(Context context, ThumbnailLocator thumbnailLocator) {
		this.context = context;
		this.thumbnailLocator = thumbnailLocator;
	}
	
	public ScaledBitmapCache(Context context, String imageDirectory) {
		this(context, new FixedDirectoryLocator(imageDirectory));
	}
	
	public Bitmap getScaledBitmap(Uri imageUri, int minWidth, int minHeight) {
		Bitmap bitmap = null;
		// check in-memory cache
		SoftReference<Bitmap> ref = scaledBitmapCache.get(imageUri);
		bitmap = (ref!=null) ? ref.get() : null;
		if (bitmap!=null) return bitmap;
		
		// check thumbnail directory
		File thumbfile = thumbnailLocator.thumbnailFileForUri(imageUri);
		if (thumbfile!=null && thumbfile.isFile()) {
			try {
				bitmap = AndroidUtils.scaledBitmapFromURIWithMinimumSize(context, 
						Uri.fromFile(thumbfile), minWidth, minHeight);
				if (bitmap!=null && bitmap.getWidth()>=minWidth && bitmap.getHeight()>=minHeight) {
					scaledBitmapCache.put(imageUri, new SoftReference(bitmap));
					return bitmap;
				}
			}
			catch(Exception ignored) {}
		}
		
		// read full-size image
		try {
			bitmap = AndroidUtils.scaledBitmapFromURIWithMinimumSize(context, imageUri, minWidth, minHeight);
		}
		catch(Exception ex) {
			bitmap = null;
		}
		if (bitmap!=null) {
			// write to in-memory map and save thumbnail image
			scaledBitmapCache.put(imageUri, new SoftReference(bitmap));
			try {
				// create thumbnail directory if it doesn't exist
				thumbfile.getParentFile().mkdirs();
				OutputStream thumbnailOutputStream = new FileOutputStream(thumbfile);
				bitmap.compress(CompressFormat.JPEG, 90, thumbnailOutputStream);
				thumbnailOutputStream.close();
			}
			catch(Exception ignored) {}
		}
		return bitmap;
	}
	
	public void removeUri(Uri imageUri) {
		scaledBitmapCache.remove(imageUri);
		thumbnailLocator.thumbnailFileForUri(imageUri).delete();
	}

}
