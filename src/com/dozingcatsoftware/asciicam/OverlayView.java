// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** View which displays the ASCII image computed from the camera preview.
 */
public class OverlayView extends View {

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    // Bitmaps are drawn offscreen in a separate thread into offscreenBitmap. When finished, the reference
    // is assigned to visibleBitmap which is drawn to the screen.
    Bitmap visibleBitmap;
    Bitmap offscreenBitmap;
    Paint textPaint = new Paint();
    
    int charPixelHeight = 9;
    int charPixelWidth = 7;
    int textSize = 10;
    
    int imageWidth;
    int imageHeight;    
    
    /**
     * Called to update the size of the camera preview image, which will be scaled to fit the view
     */
    public void updateCameraPreviewSize(int width, int height) {
        float previewRatio = ((float)width) / height;
        float viewRatio = ((float)this.getWidth()) / this.getHeight();
        if (previewRatio < viewRatio) {
            // camera preview is narrower than view, scale to full height
            this.imageHeight = this.getHeight();
            this.imageWidth = (int)(this.imageHeight * previewRatio);
        }
        else {
            this.imageWidth = this.getWidth();
            this.imageHeight = (int)(this.imageWidth / previewRatio);
        }
    }
    
    public int asciiColumnsForWidth(int width) {
        return width / charPixelWidth;
    }
    
    public int asciiRowsForHeight(int height) {
        return height / charPixelHeight;
    }
    
    public int asciiRows() {
        return asciiRowsForHeight(this.imageHeight);
    }
    public int asciiColumns() {
        return asciiColumnsForWidth(this.imageWidth);
    }
    
    @Override protected void onDraw(Canvas canvas) {
        if (visibleBitmap==null) return;
        int xoffset = (this.getWidth() - this.visibleBitmap.getWidth()) / 2;
        int yoffset = (this.getHeight() - this.visibleBitmap.getHeight()) / 2;
        canvas.drawARGB(255, 0, 0, 0);
        canvas.drawBitmap(visibleBitmap, xoffset, yoffset, null);
    }
    
    public Bitmap getVisibleBitmap() {
        return visibleBitmap;
    }
    
    void drawAscii(AsciiConverter.Result result, Canvas canvas, Paint paint) {
        canvas.drawARGB(255, 0, 0, 0);
        paint.setARGB(255, 255, 255, 255);

        paint.setTextSize(textSize);
        int rows = asciiRows();
        int cols = asciiColumns();
        if (result!=null && result.rows==rows && result.columns==cols) {
            for(int r=0; r<rows; r++) {
                int y = charPixelHeight * (r+1);
                int x = 0;
                for(int c=0; c<cols; c++) {
                    String s = result.stringAtRowColumn(r, c);
                    paint.setColor(result.colorAtRowColumn(r, c));
                    canvas.drawText(s, x, y, paint);
                    x += charPixelWidth;
                }
            }
        }
    }
    
    /**
     * Creates a bitmap from the ascii data, and invalidates itself so the new bitmap will be displayed.
     * Can be called from a background thread.
     */
    public void updateFromAsciiResult(AsciiConverter.Result result, int cameraPreviewWidth, int cameraPreviewHeight) {
        updateCameraPreviewSize(cameraPreviewWidth, cameraPreviewHeight);
        if (offscreenBitmap==null || offscreenBitmap.getWidth()!=this.imageWidth || offscreenBitmap.getHeight()!=imageHeight) {
            offscreenBitmap = Bitmap.createBitmap(this.imageWidth, this.imageHeight, Bitmap.Config.ARGB_8888);
        }
        this.drawAscii(result, new Canvas(offscreenBitmap), textPaint);
        // swap bitmaps
        Bitmap tmp = this.offscreenBitmap;
        this.offscreenBitmap = this.visibleBitmap;
        this.visibleBitmap = tmp;
        this.invalidate();
    }
    
    // For thumbnails, create image one-fourth normal size, use every other row and column, and draw solid rectangles
    // instead of text because text won't scale down well for gallery view.
    public Bitmap drawIntoThumbnailBitmap(AsciiConverter.Result result) {
        int width = this.imageWidth / 4;
        int height = this.imageHeight / 4;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setARGB(255, 255, 255, 255);

        canvas.drawARGB(255, 0, 0, 0);
        int rows = asciiRows();
        int cols = asciiColumns();
        if (result!=null && result.rows==rows && result.columns==cols) {
            for(int r=0; r<result.rows; r+=2) {
                int ymin = (int)(height*r / result.rows);
                int ymax = (int)(height*(r+2) / result.rows);
                for(int c=0; c<result.columns; c+=2) {
                    int xmin = (int)(width*c / result.columns);
                    int xmax = (int)(width*(c+2) / result.columns);
                    float ratio = result.brightnessRatioAtRowColumn(r, c);
                    paint.setColor(result.colorAtRowColumn(r, c));
                    // for full color, always draw larger rectangle because colors will be darker
                    if (result.getColorType()==AsciiConverter.ColorType.FULL_COLOR || ratio > 0.5) {
                    	canvas.drawRect(xmin, ymin, xmax, ymax, paint);
                    }
                    else {
                    	int x = (xmin + xmax) / 2 - 1;
                    	int y = (ymin + ymax) / 2 - 1;
                    	canvas.drawRect(x, y, x+2, y+2, paint);
                    }
                }
            }
        }
        return bitmap;
    }
}
