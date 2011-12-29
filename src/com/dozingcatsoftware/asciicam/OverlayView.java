package com.dozingcatsoftware.asciicam;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class OverlayView extends View {

	public OverlayView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	AsciiConverter.Result asciiResult;
	boolean whiteBackground = false;
	
	int charPixelHeight = 9;
	int charPixelWidth = 7;
	int textSize = 10;
	
	int imageWidth;
	int imageHeight;
	
	Paint textPaint = new Paint();
	
	public void setAsciiConverterResult(AsciiConverter.Result value) {
		asciiResult = value;
	}
	
	public void setHasWhiteBackground(boolean value) {
		whiteBackground = value;
	}
	
	/**
	 * Called to update the size of the camera preview image, which will be scaled to fit the view
	 */
	public void setCameraPreviewSize(int width, int height) {
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
	
	@Override
	protected void onDraw(Canvas canvas) {
		int xoffset = (this.getWidth() - this.imageWidth) / 2;
		int yoffset = (this.getHeight() - this.imageHeight) / 2; 
		drawAscii(canvas, this.getWidth(), this.getHeight(), xoffset, yoffset);
	}
	
	public void drawAscii(Canvas canvas, int width, int height, int xoffset, int yoffset) {
		if (whiteBackground) {
			canvas.drawARGB(255, 255, 255, 255);
			textPaint.setARGB(255, 0, 0, 0);
		}
		else {
			canvas.drawARGB(255, 0, 0, 0);
			textPaint.setARGB(255, 255, 255, 255);
		}
		textPaint.setTextSize(textSize);
		int rows = asciiRows();
		int cols = asciiColumns();
		if (asciiResult!=null && asciiResult.rows==rows && asciiResult.columns==cols) {
			for(int r=0; r<rows; r++) {
				int y = charPixelHeight * (r+1) + yoffset;
				int x = xoffset;
				for(int c=0; c<cols; c++) {
					String s = asciiResult.stringAtRowColumn(r, c, whiteBackground);
					if (asciiResult.hasColor()) {
						textPaint.setColor(asciiResult.colorAtRowColumn(r, c));
					}
					canvas.drawText(s, x, y, textPaint);
					x += charPixelWidth;
				}
			}
		}
		//textPaint.setARGB(255,255,0,0);
		//canvas.drawLine(104,0,104,480,textPaint);
		//canvas.drawLine(696,0,696,480,textPaint);
	}
	
	public Bitmap drawIntoNewBitmap() {
		Bitmap bitmap = Bitmap.createBitmap(this.imageWidth, this.imageHeight, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(bitmap);
		this.drawAscii(c, this.imageWidth, this.imageHeight, 0, 0);
		return bitmap;
	}
}
