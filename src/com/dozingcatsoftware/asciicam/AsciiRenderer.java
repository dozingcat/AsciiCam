package com.dozingcatsoftware.asciicam;

import java.io.IOException;
import java.io.Writer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Creates Bitmaps and HTML from AsciiConverter.Result objects.
 */
public class AsciiRenderer {
    
    Paint paint = new Paint();
    
    int charPixelHeight = 9;
    int charPixelWidth = 7;
    int textSize = 10;
    
    // Bitmaps are drawn offscreen in a separate thread into offscreenBitmap. When finished, the reference
    // is assigned to visibleBitmap which is drawn to the screen.
    Bitmap[] bitmaps = new Bitmap[2];
    int activeBitmapIndex;
    Bitmap offscreenBitmap;
    
    int maxWidth;
    int maxHeight;
    int outputImageWidth;
    int outputImageHeight;
    
    public Bitmap getVisibleBitmap() {
        return bitmaps[activeBitmapIndex];
    }

    public int getCharPixelHeight() {
        return charPixelHeight;
    }
    
    public int getCharPixelWidth() {
        return charPixelWidth;
    }
    
    public void setMaximumImageSize(int maxWidth, int maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }
    
    public void setCameraImageSize(int width, int height) {
        float cameraRatio = ((float)width) / height;
        float viewRatio = ((float)this.maxWidth) / this.maxHeight;
        if (cameraRatio < viewRatio) {
            // camera preview is narrower than view, scale to full height
            this.outputImageHeight = this.maxHeight;
            this.outputImageWidth = (int)(this.outputImageHeight * cameraRatio);
        }
        else {
            this.outputImageWidth = this.maxWidth;
            this.outputImageHeight = (int)(this.maxWidth / cameraRatio);
        }
    }
    
    public int getOutputImageWidth() {
        return this.outputImageWidth;
    }
    public int getOutputImageHeight() {
        return this.outputImageHeight;
    }
    
    public int asciiColumnsForWidth(int width) {
        return width / getCharPixelWidth();
    }
    public int asciiRowsForHeight(int height) {
        return height / getCharPixelHeight();
    }
    
    public int asciiRows() {
        return asciiRowsForHeight(this.outputImageHeight);
    }
    public int asciiColumns() {
        return asciiColumnsForWidth(this.outputImageWidth);
    }

    public void drawIntoCanvas(AsciiConverter.Result result, Canvas canvas) {
        canvas.drawARGB(255, 0, 0, 0);
        paint.setARGB(255, 255, 255, 255);

        paint.setTextSize(textSize);
        if (result!=null) {
            for(int r=0; r<result.rows; r++) {
                int y = charPixelHeight * (r+1);
                int x = 0;
                for(int c=0; c<result.columns; c++) {
                    String s = result.stringAtRowColumn(r, c);
                    paint.setColor(result.colorAtRowColumn(r, c));
                    canvas.drawText(s, x, y, paint);
                    x += charPixelWidth;
                }
            }
        }
    }
    
    public Bitmap createBitmap(AsciiConverter.Result result) {
        int nextIndex = (activeBitmapIndex + 1) % bitmaps.length;
        if (bitmaps[nextIndex]==null ||
                bitmaps[nextIndex].getWidth()!=outputImageWidth || 
                bitmaps[nextIndex].getHeight()!=outputImageHeight) {
            bitmaps[nextIndex] = Bitmap.createBitmap(outputImageWidth, outputImageHeight, Bitmap.Config.ARGB_8888);
        }
        drawIntoCanvas(result, new Canvas(bitmaps[nextIndex]));
        activeBitmapIndex = nextIndex;
        return bitmaps[activeBitmapIndex];
    }
    
    // For thumbnails, create image one-fourth normal size, use every other row and column, and draw solid rectangles
    // instead of text because text won't scale down well for gallery view.
    public Bitmap createThumbnailBitmap(AsciiConverter.Result result) {
        int width = outputImageWidth / 4;
        int height = outputImageHeight / 4;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setARGB(255, 255, 255, 255);

        canvas.drawARGB(255, 0, 0, 0);
        if (result!=null) {
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

    public void writeHtml(AsciiConverter.Result result, Writer writer, String imageName) throws IOException {
        writer.write("<html><head></title>Ascii Picture " + imageName + "</title></head>");
        writer.write("<body><div style=\"background: black; letter-spacing: 3px;\">\n");

        writer.write("<pre>");
        for(int r=0; r<result.rows; r++) {
            int lastColor = -1;
            // loop precondition: output is in the middle of a <span> tag.
            // This allows skipping the tag if it's a space or the same color as previous char.
            writer.write("<span>");
            for(int c=0; c<result.columns; c++) {
                String asciiChar = result.stringAtRowColumn(r, c);
                // don't use span tag for space
                if (" ".equals(asciiChar)) {
                    writer.write(asciiChar);
                    continue;
                }
                int color = result.colorAtRowColumn(r, c);
                if (color==lastColor) {
                    writer.write(asciiChar);
                    continue;
                }
                String htmlColor = Integer.toHexString(color & 0x00ffffff);
                while (htmlColor.length() < 6) {
                    htmlColor = "0" + htmlColor;
                }
                lastColor = color;
                writer.write(String.format("</span><span style=\"color:%s\">%s", htmlColor, asciiChar));
            }
            writer.write("</span>\n");
        }
        writer.write("</pre>\n");
        writer.write("</div></body></html>");
    }

}
