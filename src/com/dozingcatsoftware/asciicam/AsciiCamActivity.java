package com.dozingcatsoftware.asciicam;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.dozingcatsoftware.util.ARManager;
import com.dozingcatsoftware.util.CameraUtils;
import com.dozingcatsoftware.asciicam.R;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;

public class AsciiCamActivity extends Activity implements PreviewCallback {
	ARManager arManager;
	AsciiConverter asciiConverter = new AsciiConverter();
	AsciiConverter.Result asciiResult = new AsciiConverter.Result();
	
	int styleCounter = 0;
	AsciiConverter.ColorType colorType = AsciiConverter.ColorType.NONE;
	boolean whiteBackground = false;
	
	Object pictureLock = new Object();
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	setContentView(R.layout.main);
    	
    	cameraView = (SurfaceView)findViewById(R.id.cameraView);
    	overlayView = (OverlayView)findViewById(R.id.overlayView);
    	
    	arManager = ARManager.createAndSetupCameraView(this, cameraView, this);
    	arManager.setPreferredPreviewSize(640,400);
    	arManager.setNumberOfPreviewCallbackBuffers(1);

    	overlayView.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				handleMainViewTouch(event);
				return true;
			}
    	});
    }
    
    SurfaceView cameraView;
    OverlayView overlayView;
    boolean cameraViewReady = false;
    boolean appVisible = false;

    @Override
    public void onPause() {
    	appVisible = false;
    	arManager.stopCamera();
    	asciiConverter.destroyThreadPool();
    	super.onPause();
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	appVisible = true;
    	arManager.startCameraIfVisible();
    }

    public void handleMainViewTouch(MotionEvent event) {
    	if (event.getAction()==MotionEvent.ACTION_DOWN) {
    		styleCounter = (styleCounter+1) % 6;
    		colorType = AsciiConverter.ColorType.values()[styleCounter % 3];
        	whiteBackground = (styleCounter>=3);
        	overlayView.setHasWhiteBackground(whiteBackground);
    	}
    }
    
    public void takePicture(View view) {
    	try {
    		String datestr = FILENAME_DATE_FORMAT.format(new Date());
    		String dir = BASE_PICTURE_DIR + File.separator + datestr;
    		(new File(dir)).mkdirs();
    		if (!((new File(dir)).isDirectory())) {
    			return;
    		}
    		String htmlPath = saveHTML(dir, datestr);
    		String pngPath = savePNG(dir, datestr);
    		
    		Uri uri = Uri.fromFile(new File(htmlPath));
    		Intent intent = new Intent(Intent.ACTION_VIEW);
    		intent.setData(uri);
    		intent.setClassName("com.android.browser", "com.android.browser.BrowserActivity");
    		startActivity(intent);
    	}
		catch(IOException ex) {
			// TODO
		}
    }
    
    public void gotoGallery(View view) {
    	Intent intent = new Intent(this, ImageGalleryActivity.class);
    	intent.putExtra("imageDirectory", BASE_PICTURE_DIR);
    	startActivity(intent);
    }
    
    static DateFormat FILENAME_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    static String BASE_PICTURE_DIR = Environment.getExternalStorageDirectory() + File.separator + "AsciiCam";
    
    String saveHTML(String dir, String imageName) throws IOException {
		String outputFilePath;
		FileWriter output = null;
		try {
			output = new FileWriter(dir + File.separator + imageName + ".txt");
			output.write(asciiResult.getAsciiString(true));
			output.close();
			
			outputFilePath = dir + File.separator + imageName + ".html";
			output = new FileWriter(outputFilePath);
			output.write("<html><head></title>Ascii Picture " + imageName + "</title></head>");
			if (whiteBackground) {
				output.write("<body bgcolor=#ffffff>");
			}
			else {
				output.write("<body bgcolor=#000000>\n");
			}
			output.write("<pre>");
			for(int r=0; r<asciiResult.rows; r++) {
				int lastColor = -1;
				// loop precondition: output is in the middle of a <span> tag.
				// This allows skipping the tag if it's a space or the same color as previous char.
				output.write("<span>");
				for(int c=0; c<asciiResult.columns; c++) {
					String asciiChar = asciiResult.stringAtRowColumn(r, c, whiteBackground);
					// don't use span tag for space
					if (" ".equals(asciiChar)) {
						output.write(asciiChar);
						continue;
					}
					int color;
					if (asciiResult.hasColor()) {
						color = asciiResult.colorAtRowColumn(r, c);
					}
					else {
						color = (whiteBackground) ? 0xff000000 : 0xffffffff;
					}
					if (color==lastColor) {
						output.write(asciiChar);
						continue;
					}
					String htmlColor = Integer.toHexString(color & 0x00ffffff);
					while (htmlColor.length() < 6) {
						htmlColor = "0" + htmlColor;
					}
					lastColor = color;
					output.write(String.format("</span><span style=\"color:%s\">%s", htmlColor, asciiChar));
				}
				output.write("</span>\n");
			}
			output.write("</pre>\n");
			output.write("</body></html>");
			output.close();
			
		}
		finally {
			if (output!=null) output.close();
		}
    	synchronized(pictureLock) {
    		
    	}
		return outputFilePath;
    }
    
    String savePNG(String dir, String imageName) throws IOException {
    	Bitmap bitmap = overlayView.drawIntoNewBitmap();
		String outputFilePath;
		FileOutputStream output = null;
		try {
			outputFilePath = dir + File.separator + imageName + ".png";
			output = new FileOutputStream(outputFilePath);
			bitmap.compress(Bitmap.CompressFormat.PNG, 0, output);
			output.close();
		}
		finally {
			if (output!=null) output.close();
		}
    	return outputFilePath;
    }

    @Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		Camera.Size size = camera.getParameters().getPreviewSize();
		overlayView.setCameraPreviewSize(size.width, size.height);
		synchronized(pictureLock) {
			asciiConverter.computeResultForCameraData(data, size.width, size.height, 
					overlayView.asciiRows(), overlayView.asciiColumns(), colorType, null, asciiResult);
		}
		
		overlayView.setAsciiConverterResult(asciiResult);
		overlayView.postInvalidate();
		
    	CameraUtils.addPreviewCallbackBuffer(camera, data);
		
	}
}