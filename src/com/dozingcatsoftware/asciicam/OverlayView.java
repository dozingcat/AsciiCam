package com.dozingcatsoftware.asciicam;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class OverlayView extends View {

	public OverlayView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	AsciiConverter.Result asciiResult;
	int charPixelHeight = 9;
	int charPixelWidth = 7;
	int textSize = 10;
	
	Paint textPaint = new Paint();
	
	public void setAsciiConverterResult(AsciiConverter.Result value) {
		asciiResult = value;
	}
	
	public int asciiRows() {
		return this.getHeight() / charPixelHeight;
	}
	public int asciiColumns() {
		return this.getWidth() / charPixelWidth;
	}
	
	@Override
	protected void onDraw (Canvas canvas) {
		canvas.drawARGB(255, 0, 0, 0);
		textPaint.setARGB(255, 255, 255, 255);
		textPaint.setTextSize(textSize);
		int rows = asciiRows();
		int cols = asciiColumns();
		if (asciiResult!=null && asciiResult.rows==rows && asciiResult.columns==cols) {
			for(int r=0; r<rows; r++) {
				int y = charPixelHeight * (r+1);
				int x = 0;
				for(int c=0; c<cols; c++) {
					String s = asciiResult.stringAtRowColumn(r, c, false);
					if (asciiResult.hasColor) {
						textPaint.setColor(asciiResult.colorAtRowColumn(r, c));
					}
					canvas.drawText(s, x, y, textPaint);
					x += charPixelWidth;
				}
			}
		}
	}
}
