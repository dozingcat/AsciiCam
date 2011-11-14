package com.dozingcatsoftware.asciicam;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.dozingcatsoftware.util.ARManager;
import com.dozingcatsoftware.util.CameraUtils;
import com.dozingcatsoftware.asciicam.R;

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
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
	boolean useColor = true;
	
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
    	arManager.setPreferredPreviewSize(400, 240);
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
    		try {
        		savePicture();
    		}
    		catch(IOException ex) {
    			// TODO
    		}
    	}
    }
    
    static DateFormat FILENAME_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    static String BASE_PICTURE_DIR = Environment.getExternalStorageDirectory() + File.separator + "AsciiCam";
    boolean savePicture() throws IOException {
		String datestr = FILENAME_DATE_FORMAT.format(new Date());
		String dir = BASE_PICTURE_DIR + File.separator + datestr;
		if (!((new File(dir)).mkdirs())) {
			return false;
		}
		FileWriter output = null;
		try {
			output = new FileWriter(dir + File.separator + datestr + ".txt");
			output.write(asciiResult.getAsciiString(true));
			output.close();
			
			output = new FileWriter(dir + File.separator + datestr + ".html");
			output.write("<html><head></title>Ascii Picture " + datestr + "</title></head>");
			output.write("<body bgcolor=#000000>");
			output.write("<pre>");
			for(int r=0; r<asciiResult.rows; r++) {
				int lastColor = -1;
				// loop precondition: output is in the middle of a <span> tag.
				// This allows skipping the tag if it's a space or the same color as previous char.
				output.write("<span>");
				for(int c=0; c<asciiResult.columns; c++) {
					String asciiChar = asciiResult.stringAtRowColumn(r, c, false);
					// don't use span tag for 
					if (" ".equals(asciiChar)) {
						output.write(asciiChar);
						continue;
					}
					int color = asciiResult.colorAtRowColumn(r, c);
					if (color==lastColor) {
						output.write(asciiChar);
						continue;
					}
					String htmlColor = Integer.toHexString(color & 0xffffff);
					lastColor = color;
					output.write(String.format("</span><span style=\"color:%s\">%s", htmlColor, asciiChar));
				}
				output.write("</span>\n");
			}
			output.write("</pre>");
			output.write("</body></html>");
			output.close();
			
		}
		finally {
			if (output!=null) output.close();
		}
    	synchronized(pictureLock) {
    		
    	}
		return true;
    }

    @Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		Camera.Size size = camera.getParameters().getPreviewSize();
		synchronized(pictureLock) {
			asciiConverter.computeResultForCameraData(data, size.width, size.height, 
					overlayView.asciiRows(), overlayView.asciiColumns(), useColor, asciiResult);
		}
		
		overlayView.setAsciiConverterResult(asciiResult);
		overlayView.postInvalidate();
		
    	CameraUtils.addPreviewCallbackBuffer(camera, data);
		
	}
}