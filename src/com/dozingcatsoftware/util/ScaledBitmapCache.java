// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.support.v4.util.LruCache;

/** This class implements a two-level cache for Bitmaps. The first level is an in-memory map
 * which uses an LruCache so that the Bitmaps will be freed when necessary. The second
 * level is a location directory on the SD card, where smaller versions of the images
 * will be saved for faster retrieval. The exact location is determined by the ThumbnailLocator
 * implementation passed to the constructor.
 */

public class ScaledBitmapCache {
    
    static int MEMORY_CACHE_SIZE = 2*1024*1024;
	
	public static interface ThumbnailLocator {
		File thumbnailFileForUri(Uri imageUri);
	}
	
	// Simple ThumbnailLocator for putting thumbnails into a single directory using the image URI's filename.
	public static ThumbnailLocator createFixedDirectoryLocator(final String thumbnailDirectory) {
	    return new ThumbnailLocator() {
	        public File thumbnailFileForUri(Uri imageUri) {
	            String filename = imageUri.getLastPathSegment();
	            return new File(thumbnailDirectory + File.separator + filename);
	        }   
	    };
	}

	Context context;
	ThumbnailLocator thumbnailLocator;
	
	LruCache<Uri, Bitmap> scaledBitmapCache = new LruCache<Uri, Bitmap>(MEMORY_CACHE_SIZE) {
	    @Override protected int sizeOf(Uri uri, Bitmap bitmap) {
	        int size = AndroidUtils.getBitmapByteCount(bitmap);
	        return size;
	    }
	};
	
	public ScaledBitmapCache(Context context, ThumbnailLocator thumbnailLocator) {
		this.context = context;
		this.thumbnailLocator = thumbnailLocator;
	}
	
	public ScaledBitmapCache(Context context, String imageDirectory) {
		this(context, createFixedDirectoryLocator(imageDirectory));
	}
	
	public Bitmap getInMemoryScaledBitmap(Uri imageUri, int minWidth, int minHeight) {
        Bitmap bitmap = scaledBitmapCache.get(imageUri);
        if (bitmap!=null && bitmap.getWidth()>=minWidth && bitmap.getHeight()>=minHeight) {
            return bitmap;
        }
        return null;
	}
	
	public Bitmap getScaledBitmap(Uri imageUri, int minWidth, int minHeight) {
		Bitmap bitmap = getInMemoryScaledBitmap(imageUri, minWidth, minHeight);
		if (bitmap!=null) return bitmap;
		
		// check thumbnail directory
		File thumbfile = thumbnailLocator.thumbnailFileForUri(imageUri);
		if (thumbfile!=null && thumbfile.isFile()) {
			try {
				bitmap = AndroidUtils.scaledBitmapFromURIWithMinimumSize(context, 
						Uri.fromFile(thumbfile), minWidth, minHeight);
				if (bitmap!=null && bitmap.getWidth()>=minWidth && bitmap.getHeight()>=minHeight) {
					scaledBitmapCache.put(imageUri, bitmap);
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
			scaledBitmapCache.put(imageUri, bitmap);
			try {
				// create thumbnail directory if it doesn't exist
				thumbfile.getParentFile().mkdirs();
				OutputStream thumbnailOutputStream = new FileOutputStream(thumbfile);
				bitmap.compress(CompressFormat.JPEG, 90, thumbnailOutputStream);
				thumbnailOutputStream.close();
				// create .noindex file so thumbnail pictures won't be indexed and show up in the gallery app
				(new File(thumbfile.getParentFile().getPath() + File.separator + ".nomedia")).createNewFile();
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
