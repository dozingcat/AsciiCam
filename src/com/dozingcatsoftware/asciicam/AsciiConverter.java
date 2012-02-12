// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/** This class converts pixel data received from the camera into ASCII characters using brightness
 * and color information. 
 * @author brian
 *
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
        
        public String getDebugInfo() {
            return debugInfo;
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
     * @author brian
     *
     */
    class Worker implements Callable {
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
        
        public Object call() {
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
    List threadWorkers;
    
    public void initThreadPool(int numThreads) {
        destroyThreadPool();
        if (numThreads<=0) numThreads = Runtime.getRuntime().availableProcessors();
        threadPool = Executors.newFixedThreadPool(numThreads);
        threadWorkers = new ArrayList();
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
    
    public Result resultForCameraData(byte[] data, int imageWidth, int imageHeight,
            int asciiRows, int asciiCols, String pixelCharString, ColorType colorType) {
        Result result = new Result();
        computeResultForCameraData(data, imageWidth, imageHeight, asciiRows, asciiCols, colorType, pixelCharString, result);
        return result;
    }
    
    public void computeResultForCameraData(byte[] data, int imageWidth, int imageHeight,
            int asciiRows, int asciiCols, ColorType colorType, String pixelCharString, Result result) {
        long t1 = System.nanoTime();
        result.debugInfo = null;
        if (threadPool==null) {
            initThreadPool(0);
        }
        for(Object worker : threadWorkers) {
            ((Worker)worker).setValues(data, imageWidth, imageHeight, asciiRows, asciiCols, toPixelCharArray(pixelCharString), colorType, result);
        }
        try {
        	// invoke call() method of all workers and wait for them to finish
            List threadTimes = threadPool.invokeAll(threadWorkers);
            if (DEBUG) {
                long t2 = System.nanoTime();
                StringBuilder builder = new StringBuilder();
                for(int i=0; i<threadTimes.size(); i++) {
                    try {
                        long threadNanos = (Long)((FutureTask)threadTimes.get(i)).get();
                        builder.append(String.format("Thread %d time: %d ms", i+1, threadNanos/1000000)).append("\n");
                    }
                    catch(ExecutionException ex) {}
                }
                builder.append(String.format("Total time: %d ms", (t2-t1) / 1000000));
                result.debugInfo = builder.toString();
            }
        }
        catch(InterruptedException ignored) {}
    }
    
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
            // For ANSI mode, if a color component (red/green/blue) is at least this fraction of the maximum
            // component, turn it on. {red=200, green=180, blue=160} would become yellow: green ratio is
            // 0.9 so it's enabled, blue is 0.8 so it isn't.
            final float ANSI_COLOR_RATIO = 7.0f/8;
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

}
