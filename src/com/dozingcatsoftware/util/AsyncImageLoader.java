// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.util;

import java.lang.ref.WeakReference;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.ImageView;

/**
 * Class to handle assigning a bitmap to a view, when the bitmap may need to be loaded from
 * storage asynchronously.
 *
 * This code is based on the sample code at http://developer.android.com/training/displaying-bitmaps/index.html
 */
public class AsyncImageLoader {

    static class BitmapWorkerTask extends AsyncTask<Uri, Void, Bitmap> {
        WeakReference<ImageView> imageViewReference;
        Uri data = null;
        ScaledBitmapCache bitmapCache;
        int width;
        int height;

        public BitmapWorkerTask(ImageView imageView, ScaledBitmapCache bitmapCache, int width, int height) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            imageViewReference = new WeakReference<ImageView>(imageView);
            this.bitmapCache = bitmapCache;
            this.width = width;
            this.height = height;
        }

        // Decode image in background.
        @Override protected Bitmap doInBackground(Uri...args) {
            data = args[0];
            return bitmapCache.getScaledBitmap(args[0], width, height);
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override protected void onPostExecute(Bitmap bitmap) {
            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }

        private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
            if (imageView != null) {
                final Drawable drawable = imageView.getDrawable();
                if (drawable instanceof AsyncDrawable) {
                    final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                    return asyncDrawable.getBitmapWorkerTask();
                }
            }
            return null;
        }

        public static boolean cancelPotentialWork(Uri uri, ImageView imageView) {
            final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

            if (bitmapWorkerTask != null) {
                final Uri taskUri = bitmapWorkerTask.data;
                if (!uri.equals(taskUri)) {
                    // Cancel previous task
                    bitmapWorkerTask.cancel(true);
                }
                else {
                    // The same work is already in progress
                    return false;
                }
            }
            // No task associated with the ImageView, or an existing task was cancelled
            return true;
        }
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, BitmapWorkerTask bitmapWorkerTask) {
            super(res, (Bitmap)null);
            bitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    /**
     * Sets the bitmap of an ImageView, loading the bitmap using a background AsyncTask if needed.
     * If the image URI is found in-memory in the ScaledBitmapCache, assigns it directly to the
     * ImageView and returns. Otherwise, creates an AsyncTask in which the ScaledBitmapCache reads
     * the bitmap from secondary storage and assigns it to the ImageView when loaded.
     */
    public void loadImageIntoViewAsync(final ScaledBitmapCache bitmapCache, Uri imageUri, ImageView imageView,
            final int width, final int height, Resources resources) {
        // check in-memory cache, if found no need for an AsyncTask
        Bitmap bitmap = bitmapCache.getInMemoryScaledBitmap(imageUri, width, height);
        if (bitmap!=null) {
            imageView.setImageBitmap(bitmap);
            return;
        }
        // read from storage with AsyncTask
        if (BitmapWorkerTask.cancelPotentialWork(imageUri, imageView)) {
            BitmapWorkerTask task = new BitmapWorkerTask(imageView, bitmapCache, width, height);
            AsyncDrawable asyncDrawable = new AsyncDrawable(resources, task);
            imageView.setImageDrawable(asyncDrawable);
            task.execute(imageUri);
        }
    }

}
