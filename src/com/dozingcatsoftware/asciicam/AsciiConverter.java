package com.dozingcatsoftware.asciicam;

public class AsciiConverter {
	
	static String[] PIXEL_CHARS = new String[] {
		" ", ".", ":", "o", "O", "8", "@",
	};
	
	public static class Result {
		public int rows;
		public int columns;
		public boolean hasColor;
		
		int[] asciiIndexes;
		int[] asciiColors;
		
		public String stringAtRowColumn(int row, int col, boolean whiteBackground) {
			return (whiteBackground) ? PIXEL_CHARS[PIXEL_CHARS.length-1-asciiIndexes[row*columns + col]] :
				PIXEL_CHARS[asciiIndexes[row*columns + col]];
		}
		
		public int colorAtRowColumn(int row, int col) {
			return asciiColors[row*columns + col];
		}
		
		public String getAsciiString(boolean includeNewlines) {
			StringBuilder buffer = new StringBuilder();
			int index = 0;
			for(int r=0; r<rows; r++) {
				for(int c=0; c<columns; c++) {
					buffer.append(PIXEL_CHARS[asciiIndexes[index++]]);
				}
				if (includeNewlines) buffer.append("\n");
			}
			return buffer.toString();
		}
	}
	
	static boolean nativeCodeAvailable = false;
	
	public native void getAsciiValuesWithColorNative(byte[] jdata, int imageWidth, int imageHeight, 
			int asciiRows, int asciiCols, int numAsciiChars, int[] jasciiOutput, int[] jcolorOutput);

	public native void getAsciiValuesBWNative(byte[] jdata, int imageWidth, int imageHeight, 
			int asciiRows, int asciiCols, int numAsciiChars, int[] jasciiOutput);

	static {
		try {
			System.loadLibrary("asciiart");
			nativeCodeAvailable = true;
		}
		catch(Throwable ignored) {}
	}
	
	public Result resultForCameraData(byte[] data, int imageWidth, int imageHeight,
			int asciiRows, int asciiCols, boolean useColor) {
		Result result = new Result();
		computeResultForCameraData(data, imageWidth, imageHeight, asciiRows, asciiCols, useColor, result);
		return result;
	}
	
	public void computeResultForCameraData(byte[] data, int imageWidth, int imageHeight,
			int asciiRows, int asciiCols, boolean useColor, Result result) {
		result.rows = asciiRows;
		result.columns = asciiCols;
		result.hasColor = useColor;
		
		if (result.asciiIndexes==null || result.asciiIndexes.length!=asciiRows*asciiCols) {
			result.asciiIndexes = new int[asciiRows * asciiCols];
		}
		
		if (useColor) {
			if (result.asciiColors==null || result.asciiIndexes.length!=asciiRows*asciiCols) {
				result.asciiColors = new int[asciiRows * asciiCols];
			}
			
			if (nativeCodeAvailable) {
				getAsciiValuesWithColorNative(data, imageWidth, imageHeight, asciiRows, asciiCols, 
						PIXEL_CHARS.length, result.asciiIndexes, result.asciiColors);
				return;
			}
			
			final int MAX_COLOR_VAL = 262143; // 2**18-1
			int asciiIndex = 0;
			for(int r=0; r<asciiRows; r++) {
				// compute grid of data pixels whose brightness to average
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
						int uvoffset = imageWidth * imageHeight + (imageWidth * (y / 2));
						for(int x=xmin; x<xmax; x++) {
							samples++;
							int bright = 0xff & data[rowoffset+x];
							totalBright += bright;
							// YUV to RGB conversion
							int yy = bright - 16;
							if (yy < 0) yy = 0;
							
							int uvindex = uvoffset + (x & ~1);
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
					result.asciiIndexes[asciiIndex] = (averageBright * PIXEL_CHARS.length) / 255;
					int averageRed = totalRed / samples;
					int averageGreen = totalGreen / samples;
					int averageBlue = totalBlue / samples;
					
					// force highest color component to maximum (brightness is already handled by char)
					int maxRG = (averageRed > averageGreen) ? averageRed : averageGreen;
					int maxColor = (averageBlue > maxRG) ? averageBlue : maxRG;
					if (maxColor==0) {
						averageRed = averageGreen = averageBlue = MAX_COLOR_VAL;
					}
					else {
						float scaleFactor = 1.0f*MAX_COLOR_VAL / maxColor;
						// don't exceed MAX_COLOR_VAL via floating point rounding
						averageRed = Math.min((int)(averageRed*scaleFactor), MAX_COLOR_VAL);
						averageGreen = Math.min((int)(averageGreen*scaleFactor), MAX_COLOR_VAL);
						averageBlue = Math.min((int)(averageBlue*scaleFactor), MAX_COLOR_VAL);
					}
					result.asciiColors[asciiIndex] = (0xff000000) | ((averageRed << 6) & 0xff0000) |
					                                ((averageGreen >> 2) & 0xff00) | ((averageBlue >> 10));
					++asciiIndex;
				}
			}
			
		}
		else {
			if (nativeCodeAvailable) {
				getAsciiValuesBWNative(data, imageWidth, imageHeight, asciiRows, asciiCols, 
						PIXEL_CHARS.length, result.asciiIndexes);
				return;
			}

			int asciiIndex = 0;
			for(int r=0; r<asciiRows; r++) {
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
					result.asciiIndexes[asciiIndex++] = (averageBright * PIXEL_CHARS.length) / 255;
				}
			}
		}
	}

}
