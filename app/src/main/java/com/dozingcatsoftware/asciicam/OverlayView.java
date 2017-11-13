// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.View;

/** View which displays the ASCII image computed from the camera preview. */
public class OverlayView extends View {

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    Bitmap bitmap;

    boolean flipHorizontal;
    Matrix flipHorizontalMatrix = new Matrix();
    int backgroundFillColor = Color.argb(255, 0, 0, 0);

    public void setFlipHorizontal(boolean value) {
        this.flipHorizontal = value;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (bitmap==null) return;
        int xoffset = (this.getWidth() - this.bitmap.getWidth()) / 2;
        int yoffset = (this.getHeight() - this.bitmap.getHeight()) / 2;
        canvas.drawColor(backgroundFillColor);
        if (flipHorizontal) {
            flipHorizontalMatrix.setScale(-1,1);
            flipHorizontalMatrix.postTranslate(bitmap.getWidth() + xoffset, yoffset);
            canvas.drawBitmap(bitmap, flipHorizontalMatrix, null);
        }
        else {
            canvas.drawBitmap(bitmap, xoffset, yoffset, null);
        }
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap value) {
        this.bitmap = value;
    }

    public void setBackgroundFillColor(int color) {
        this.backgroundFillColor = color;
    }

}
