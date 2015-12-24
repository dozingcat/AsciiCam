// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import android.graphics.Bitmap;

/**
 * This class converts pixel data received from the camera into ASCII characters using brightness
 * and color information.
 */
public class AsciiConverter {

    static final boolean DEBUG = false;

    public static enum ColorType {
    	// all same color
        NONE(" .:oO8@"),

        // primary colors and combinations (red/green/blue/cyan/magenta/yellow/white)
        ANSI_COLOR(" .:oO8@"),

        // all colors
        FULL_COLOR("O8@");

        String[] pixelChars;

        private ColorType(String pixelCharString) {
        	this.pixelChars = toPixelCharArray(pixelCharString);
        }

        public String[] getDefaultPixelChars() {
        	return pixelChars;
        }
    }

    public static enum Orientation {
        NORMAL,
        ROTATED_180,
    }

    /** Holds the result of computing ASCII output from the input camera data.
     */
    public static class Result {
        public int rows;
        public int columns;
        public ColorType colorType;
        String[] pixelChars;
        String debugInfo;

        int[] asciiIndexes;
        int[] asciiColors;

        public ColorType getColorType() {
            return colorType;
        }

        public String stringAtRowColumn(int row, int col) {
            return pixelChars[asciiIndexes[row*columns + col]];
        }

        public int colorAtRowColumn(int row, int col) {
            if (colorType==ColorType.NONE) return 0xffffffff;
            return asciiColors[row*columns + col];
        }

        public float brightnessRatioAtRowColumn(int row, int col) {
            return 1.0f*asciiIndexes[row*columns + col] / pixelChars.length;
        }

        private void rotateImage180Degrees() {
            // Reverse the character and (if present) color arrays.
            for (int front=0, back=asciiIndexes.length-1; front<back; front++, back--) {
                int tmp = asciiIndexes[front];
                asciiIndexes[front] = asciiIndexes[back];
                asciiIndexes[back] = tmp;
                if (asciiColors != null) {
                    tmp = asciiColors[front];
                    asciiColors[front] = asciiColors[back];
                    asciiColors[back] = tmp;
                }
            }
        }

        public void adjustForOrientation(Orientation orientation) {
            switch (orientation) {
                case ROTATED_180:
                    rotateImage180Degrees();
                    break;
                default:
                    break;
            }
        }

        public String getDebugInfo() {
            return debugInfo;
        }

        public Result copy() {
            Result rcopy = new Result();
            rcopy.rows = this.rows;
            rcopy.columns = this.columns;
            rcopy.colorType = this.colorType;
            if (pixelChars!=null) rcopy.pixelChars = pixelChars.clone();
            if (asciiIndexes!=null) rcopy.asciiIndexes = asciiIndexes.clone();
            if (asciiColors!=null) rcopy.asciiColors = asciiColors.clone();
            return rcopy;
        }
    }

    static boolean nativeCodeAvailable = false;

    public native void getAsciiValuesWithColorNative(byte[] jdata, int imageWidth, int imageHeight,
            int asciiRows, int asciiCols, int numAsciiChars, boolean ansiColor,
            int[] jasciiOutput, int[] jcolorOutput, int startRow, int endRow);

    public native void getAsciiValuesBWNative(byte[] jdata, int imageWidth, int imageHeight,
            int asciiRows, int asciiCols, int numAsciiChars, int[] jasciiOutput, int startRow, int endRow);

    static {
        try {
            System.loadLibrary("asciiart");
            nativeCodeAvailable = true;
        }
        catch(Throwable ignored) {}
    }

    /** Image processing can be broken up into multiple workers, with each worker computing a portion of
     * the image rows. This allows using all CPU cores on multicore devices.
     */
    class Worker implements Callable<Long> {
        // identifies which segment this worker will compute
        int totalSegments;
        int segmentNumber;
        // image parameters set for every frame in setValues
        byte[] data;
        int imageWidth;
        int imageHeight;
        int asciiRows;
        int asciiColumns;
        String[] pixelChars;
        ColorType colorType;
        Result result;

        public Worker(int totalSegments, int segmentNumber) {
            this.totalSegments = totalSegments;
            this.segmentNumber = segmentNumber;
        }

        public void setValues(byte[] data, int imageWidth, int imageHeight, int asciiRows, int asciiColumns,
                String[] pixelChars, ColorType colorType, Result result) {
            this.data = data;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.asciiRows = asciiRows;
            this.asciiColumns = asciiColumns;
            this.pixelChars = pixelChars;
            this.colorType = colorType;
            this.result = result;
        }

        @Override public Long call() {
            // returns time in nanoseconds to execute
            long t1 = System.nanoTime();
            int startRow = asciiRows * segmentNumber / totalSegments;
            int endRow = asciiRows * (segmentNumber + 1) / totalSegments;
            computeResultForRows(data, imageWidth, imageHeight, asciiRows, asciiColumns,
                    colorType, pixelChars, result, startRow, endRow);
            return System.nanoTime() - t1;
        }
    }

    ExecutorService threadPool;
    List<Worker> threadWorkers;

    public void initThreadPool(int numThreads) {
        destroyThreadPool();
        if (numThreads<=0) numThreads = Runtime.getRuntime().availableProcessors();
        threadPool = Executors.newFixedThreadPool(numThreads);
        threadWorkers = new ArrayList<Worker>();
        for(int i=0; i<numThreads; i++) {
            threadWorkers.add(new Worker(numThreads, i));
        }
    }

    public void destroyThreadPool() {
        if (threadPool!=null) {
            threadPool.shutdown();
            threadPool = null;
        }
    }

    private static String[] toPixelCharArray(String str) {
        if (str==null || str.length()==0) return null;
        String[] charArray = new String[str.length()];
        for(int i=0; i<str.length(); i++) {
            charArray[i] = str.substring(i, i+1);
        }
        return charArray;
    }

    public void computeResultForCameraData(
            byte[] data, int imageWidth, int imageHeight, int asciiRows, int asciiCols,
            ColorType colorType, String pixelCharString, Orientation orientation,
            Result result) {
        long t1 = System.nanoTime();
        result.debugInfo = null;
        if (threadPool==null) {
            initThreadPool(0);
        }
        for(Worker worker : threadWorkers) {
            worker.setValues(data, imageWidth, imageHeight, asciiRows, asciiCols, toPixelCharArray(pixelCharString), colorType, result);
        }
        try {
        	// invoke call() method of all workers and wait for them to finish
            List<Future<Long>> threadTimes = threadPool.invokeAll(threadWorkers);
            result.adjustForOrientation(orientation);
            if (DEBUG) {
                long t2 = System.nanoTime();
                StringBuilder builder = new StringBuilder();
                for(int i=0; i<threadTimes.size(); i++) {
                    try {
                        long threadNanos = threadTimes.get(i).get();
                        builder.append(String.format("Thread %d time: %d ms", i+1, threadNanos/1000000)).append("\n");
                    }
                    catch(ExecutionException ex) {}
                }
                builder.append(String.format("Total time: %d ms", (t2-t1) / 1000000));
                result.debugInfo = builder.toString();
                android.util.Log.i("Timing", result.debugInfo);
            }
        }
        catch(InterruptedException ignored) {}
    }

    // For ANSI mode, if a color component (red/green/blue) is at least this fraction of the maximum
    // component, turn it on. {red=200, green=180, blue=160} would become yellow: green ratio is
    // 0.9 so it's enabled, blue is 0.8 so it isn't.
    final float ANSI_COLOR_RATIO = 7.0f/8;

    /** Main computation method. Takes camera input data, number of ASCII rows and columns to convert to, and the ASCII
     * characters to use ordered by brightness. For each ASCII character in the output, determines the corresponding
     * rectangle of pixels in the input image and computes the average brightness and RGB components if using color.
     */
    private void computeResultForRows(byte[] data, int imageWidth, int imageHeight,
            int asciiRows, int asciiCols, ColorType colorType, String[] pixelChars, Result result,
            int startRow, int endRow) {
        result.rows = asciiRows;
        result.columns = asciiCols;
        result.colorType = colorType;
        if (pixelChars==null) pixelChars = colorType.getDefaultPixelChars();
        result.pixelChars = pixelChars;

        if (result.asciiIndexes==null || result.asciiIndexes.length!=asciiRows*asciiCols) {
            result.asciiIndexes = new int[asciiRows * asciiCols];
        }

        if (colorType!=ColorType.NONE) {
            if (result.asciiColors==null || result.asciiIndexes.length!=asciiRows*asciiCols) {
                result.asciiColors = new int[asciiRows * asciiCols];
            }

            if (nativeCodeAvailable) {
                getAsciiValuesWithColorNative(data, imageWidth, imageHeight, asciiRows, asciiCols,
                        pixelChars.length, colorType==ColorType.ANSI_COLOR, result.asciiIndexes, result.asciiColors,
                        startRow, endRow);
                return;
            }

            final int MAX_COLOR_VAL = (1 << 18) - 1;
            int asciiIndex = startRow * asciiCols;
            for(int r=startRow; r<endRow; r++) {
                // compute grid of data pixels whose brightness and colors to average
                int ymin = imageHeight * r / asciiRows;
                int ymax = imageHeight * (r+1) / asciiRows;
                for(int c=0; c<asciiCols; c++) {
                    int xmin = imageWidth * c / asciiCols;
                    int xmax = imageWidth * (c+1) / asciiCols;

                    int totalBright = 0;
                    int totalRed=0, totalGreen=0, totalBlue=0;
                    int samples = 0;
                    for(int y=ymin; y<ymax; y++) {
                        int rowoffset = imageWidth * y;
                        // UV data is only stored for every other row and column, so there are 1/4 as many (U,V) byte
                        // pairs as there are pixels (and 1/2 as many total UV bytes).
                        int uvoffset = imageWidth * imageHeight + (imageWidth * (y / 2));
                        for(int x=xmin; x<xmax; x++) {
                            samples++;
                            int bright = 0xff & data[rowoffset+x];
                            totalBright += bright;
                            // YUV to RGB conversion, produces 18-bit RGB components
                            // adapted from http://stackoverflow.com/questions/8399411/how-to-retrieve-rgb-value-for-each-color-apart-from-one-dimensional-integer-rgb
                            int yy = bright - 16;
                            if (yy < 0) yy = 0;

                            int uvindex = uvoffset + (x & ~1); // 0, 0, 2, 2, 4, 4...
                            int v = (0xff & data[uvindex]) - 128;
                            int u = (0xff & data[uvindex + 1]) - 128;
                            int y1192 = 1192 * yy;
                            int red = (y1192 + 1634 * v);
                            int green = (y1192 - 833 * v - 400 * u);
                            int blue = (y1192 + 2066 * u);

                            if (red<0) red=0; if (red>MAX_COLOR_VAL) red=MAX_COLOR_VAL;
                            if (green<0) green=0; if (green>MAX_COLOR_VAL) green=MAX_COLOR_VAL;
                            if (blue<0) blue=0; if (blue>MAX_COLOR_VAL) blue=MAX_COLOR_VAL;

                            totalRed += red;
                            totalGreen += green;
                            totalBlue += blue;
                        }
                    }
                    int averageBright = totalBright / samples;
                    result.asciiIndexes[asciiIndex] = (averageBright * pixelChars.length) / 256;
                    int averageRed = totalRed / samples;
                    int averageGreen = totalGreen / samples;
                    int averageBlue = totalBlue / samples;

                    // for ANSI mode, force each RGB component to be either max or 0
                    if (colorType==ColorType.ANSI_COLOR) {
                        // Force highest color component to maximum (brightness is already handled by char).
                    	// Other components go to maximum if their ratio to the highest component is at least ANSI_COLOR_RATIO.
                        int maxRG = (averageRed > averageGreen) ? averageRed : averageGreen;
                        int maxColor = (averageBlue > maxRG) ? averageBlue : maxRG;
                        if (maxColor > 0) {
                            int threshold = (int)(maxColor * ANSI_COLOR_RATIO);
                            averageRed = (averageRed >= threshold) ? MAX_COLOR_VAL : 0;
                            averageGreen = (averageGreen >= threshold) ? MAX_COLOR_VAL : 0;
                            averageBlue = (averageBlue >= threshold) ? MAX_COLOR_VAL : 0;
                        }
                    }
                    result.asciiColors[asciiIndex] = (0xff000000) | ((averageRed << 6) & 0xff0000) |
                                                    ((averageGreen >> 2) & 0xff00) | ((averageBlue >> 10));
                    ++asciiIndex;
                }
            }

        }
        else {
        	// black and white mode; we only need to look at pixel brightness
            if (nativeCodeAvailable) {
                getAsciiValuesBWNative(data, imageWidth, imageHeight, asciiRows, asciiCols,
                        pixelChars.length, result.asciiIndexes, startRow, endRow);
                return;
            }

            int asciiIndex = startRow * asciiCols;
            for(int r=startRow; r<endRow; r++) {
                // compute grid of data pixels whose brightness to average
                int ymin = imageHeight * r / asciiRows;
                int ymax = imageHeight * (r+1) / asciiRows;
                for(int c=0; c<asciiCols; c++) {
                    int xmin = imageWidth * c / asciiCols;
                    int xmax = imageWidth * (c+1) / asciiCols;

                    int totalBright = 0;
                    int samples = 0;
                    for(int y=ymin; y<ymax; y++) {
                        int rowoffset = imageWidth * y;
                        for(int x=xmin; x<xmax; x++) {
                            samples++;
                            totalBright += (0xff & data[rowoffset+x]);
                        }
                    }
                    int averageBright = totalBright / samples;
                    result.asciiIndexes[asciiIndex++] = (averageBright * pixelChars.length) / 256;
                }
            }
        }
    }

    /** Builds an ASCII image from an existing bitmap. Used to convert existing pictures; not
     * native or threaded because speed is less important.
     */
    public Result computeResultForBitmap(Bitmap bitmap,
            int asciiRows, int asciiCols, ColorType colorType, String pixelCharString) {
        Result result = new Result();
        result.rows = asciiRows;
        result.columns = asciiCols;
        result.colorType = colorType;
        result.asciiColors = new int[asciiRows*asciiCols];
        result.asciiIndexes = new int[asciiRows*asciiCols];
        result.pixelChars = (pixelCharString!=null) ?
                toPixelCharArray(pixelCharString) : colorType.getDefaultPixelChars();

        // TODO: read bitmap pixels into array for faster processing
        int[] pixels = null;
        int asciiIndex = 0;
        for(int r=0; r<asciiRows; r++) {
            // compute grid of data pixels whose brightness and colors to average
            int ymin = bitmap.getHeight() * r / asciiRows;
            int ymax = bitmap.getHeight() * (r+1) / asciiRows;
            // read all pixels for this row of characters
            if (pixels==null) pixels = new int[(ymax-ymin+1) * bitmap.getWidth()];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, ymin, bitmap.getWidth(), ymax-ymin);
            for(int c=0; c<asciiCols; c++) {
                int xmin = bitmap.getWidth() * c / asciiCols;
                int xmax = bitmap.getWidth() * (c+1) / asciiCols;

                int totalBright = 0;
                int totalRed=0, totalGreen=0, totalBlue=0;
                int samples = 0;

                for(int y=ymin; y<ymax; y++) {
                    int poffset = (y-ymin)*bitmap.getWidth() + xmin;
                    for(int x=xmin; x<xmax; x++) {
                        samples++;

                        int color = pixels[poffset++];
                        //int color = bitmap.getPixel(x, y);
                        int red = (color >> 16) & 0xff;
                        int green = (color >> 8) & 0xff;
                        int blue = color & 0xff;
                        // Y = 0.299R + 0.587G + 0.114B
                        totalBright += (int)(0.299*red + 0.587*green + 0.114*blue);
                        totalRed += red;
                        totalGreen += green;
                        totalBlue += blue;
                    }
                }
                int averageBright = totalBright / samples;
                result.asciiIndexes[asciiIndex] = (averageBright * result.pixelChars.length) / 256;
                if (DEBUG) {
                    if (asciiIndex%50==0) {
                        android.util.Log.i("color", String.format("%d %d %d %d",
                                averageBright, samples, result.pixelChars.length, result.asciiIndexes[asciiIndex]));
                    }
                }

                if (colorType!=ColorType.NONE) {
                    int averageRed = totalRed / samples;
                    int averageGreen = totalGreen / samples;
                    int averageBlue = totalBlue / samples;
                    // for ANSI mode, force each RGB component to be either max or 0
                    if (colorType==ColorType.ANSI_COLOR) {
                        // Force highest color component to maximum (brightness is already handled by char).
                        // Other components go to maximum if their ratio to the highest component is at least ANSI_COLOR_RATIO.
                        int maxRG = (averageRed > averageGreen) ? averageRed : averageGreen;
                        int maxColor = (averageBlue > maxRG) ? averageBlue : maxRG;
                        if (maxColor > 0) {
                            int threshold = (int)(maxColor * ANSI_COLOR_RATIO);
                            averageRed = (averageRed >= threshold) ? 255 : 0;
                            averageGreen = (averageGreen >= threshold) ? 255 : 0;
                            averageBlue = (averageBlue >= threshold) ? 255 : 0;
                        }
                    }
                    result.asciiColors[asciiIndex] = (0xff000000) | (averageRed << 16) |
                                                    (averageGreen << 8) | averageBlue;
                }
                ++asciiIndex;
            }
        }
        return result;
    }

}
