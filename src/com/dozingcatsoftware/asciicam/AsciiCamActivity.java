// Copyright (C) 2012-2016 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.Toast;

import com.dozingcatsoftware.util.ARManager;
import com.dozingcatsoftware.util.AndroidUtils;
import com.dozingcatsoftware.util.AsyncProcessor;
import com.dozingcatsoftware.util.CameraUtils;
import com.dozingcatsoftware.util.ShutterButton;

public class AsciiCamActivity extends Activity
implements Camera.PreviewCallback, ShutterButton.OnShutterButtonListener {

    private final static boolean DEBUG = false;
    private final static String TAG = "AsciiCamActivity";

    // Pixels and metadata for a preview frame.
    static class CameraPreviewData {
        public final Camera camera;
        public final CameraUtils.CameraInfo cameraInfo;
        public final byte[] pixelData;
        public final int width;
        public final int height;
        public final long timestamp;
        public CameraPreviewData(Camera c, CameraUtils.CameraInfo i, byte[] p, int w, int h, long t) {
            camera = c;
            cameraInfo = i;
            pixelData = p;
            width = w;
            height = h;
            timestamp = t;
        }
    }

    ARManager arManager;
    AsciiConverter asciiConverter = new AsciiConverter();
    AsciiConverter.Result asciiResult = new AsciiConverter.Result();

    AsciiConverter.ColorType colorType = AsciiConverter.ColorType.ANSI_COLOR;
    Map<AsciiConverter.ColorType, String> pixelCharsMap = new EnumMap<AsciiConverter.ColorType, String>(AsciiConverter.ColorType.class);

    final static int ACTIVITY_PREFERENCES = 1;
    final static int ACTIVITY_PICK_IMAGE = 2;

    ImageButton cycleColorButton;
    ShutterButton shutterButton;
    SurfaceView cameraView;
    OverlayView overlayView;

    Handler handler = new Handler();
    boolean cameraViewReady = false;
    boolean appVisible = false;
    boolean saveInProgress = false;

    AsciiRenderer imageRenderer = new AsciiRenderer();
    AsciiImageWriter imageWriter = new AsciiImageWriter();

    AsyncProcessor<CameraPreviewData, Bitmap> imageProcessor;
    // If imageProcessor is busy when a preview frame arrives, store it here so that when it
    // finishes the current frame it can immediately process the next one without having to
    // wait until another frame arrives.
    CameraPreviewData nextPreviewData = null;

    /** Called when the activity is first created. */
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);

        cameraView = (SurfaceView)findViewById(R.id.cameraView);
        overlayView = (OverlayView)findViewById(R.id.overlayView);
        cycleColorButton = (ImageButton)findViewById(R.id.cycleColorButton);
        shutterButton = (ShutterButton)findViewById(R.id.shutterButton);
        shutterButton.setOnShutterButtonListener(this);

        arManager = ARManager.createAndSetupCameraView(this, cameraView, this);
        arManager.setPreferredPreviewSize(640,400);
        // Having 2 buffers lets us store a subsequent frame if the previous frame is still being processed.
        arManager.setNumberOfPreviewCallbackBuffers(2);

        findViewById(R.id.switchCameraButton).setVisibility(CameraUtils.numberOfCameras() > 1 ? View.VISIBLE : View.GONE);
        updateFromPreferences();
        updateColorButton();
    }

    @Override public void onPause() {
        appVisible = false;
        arManager.stopCamera();
        asciiConverter.destroyThreadPool();
        imageRenderer.destroyThreadPool();
        if (imageProcessor != null) {
            imageProcessor.stop();
            imageProcessor = null;
        }
        super.onPause();
    }

    @Override public void onResume() {
        super.onResume();
        appVisible = true;
        arManager.startCameraIfVisible();
        imageProcessor = new AsyncProcessor<CameraPreviewData, Bitmap>();
        imageProcessor.start();
        AndroidUtils.setSystemUiLowProfile(cameraView);
    }

    void updateFromPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        for(AsciiConverter.ColorType colorType : AsciiConverter.ColorType.values()) {
            String prefsKey = getString(R.string.pixelCharsPrefIdPrefix) + colorType.name();
            pixelCharsMap.put(colorType, prefs.getString(prefsKey, ""));
        }

        String colorTypeName = prefs.getString("colorType", null);
        if (colorTypeName!=null) {
            try {
                this.colorType = AsciiConverter.ColorType.valueOf(colorTypeName);;
            }
            catch(Exception ignored) {}
        }
        if (colorType==null) {
            colorType = AsciiConverter.ColorType.ANSI_COLOR;
        }
        AsciiCamPreferences.setAutoConvertEnabled(this, prefs.getBoolean(getString(R.string.autoConvertPicturesPrefId), false));
    }

    void saveColorStyleToPreferences() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit();
        editor.putString("colorType", colorType.name());
        editor.commit();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, final Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        switch(requestCode) {
            case ACTIVITY_PREFERENCES:
                updateFromPreferences();
                break;
            case ACTIVITY_PICK_IMAGE:
                if (resultCode==RESULT_OK) {
                    (new Thread() {
                        @Override public void run() {
                            try {
                                final String imagePath = (new ProcessImageOperation()).
                                        processImage(AsciiCamActivity.this, intent.getData());
                                handler.post(new Runnable() {
                                    @Override public void run() {
                                        ViewImageActivity.startActivityWithImageURI(AsciiCamActivity.this,
                                                Uri.fromFile(new File(imagePath)), "image/png");
                                    }
                                });
                            }
                            catch(Exception ex) {
                                Log.e(TAG, "Failed converting image", ex);
                            }
                        }
                    }).start();
                }
                break;
        }
    }

    void takePictureThreadEntry(final AsciiConverter.Result result) {
        try {
            final String pngPath = imageWriter.saveImageAndThumbnail(
                    imageRenderer.getVisibleBitmap(),
                    imageRenderer.createThumbnailBitmap(result),
                    result);
            handler.post(new Runnable() {
                @Override public void run() {
                    bitmapSaved(pngPath, "image/png");
                }
            });
        }
        catch(IOException ex) {
            Log.e(TAG, "Error saving picture", ex);
        }
        finally {
            handler.post(new Runnable() {
                @Override public void run() {
                    saveInProgress = false;
                }
            });
        }
    }

    void takePicture() {
        saveInProgress = true;
        // Use a separate thread to write the PNG and HTML files, so the UI doesn't block.
        (new Thread() {
            @Override public void run() {
                AsciiConverter.Result savePictureResult = null;
                synchronized (asciiResult) {
                    savePictureResult = asciiResult.copy();
                }
                takePictureThreadEntry(savePictureResult);
            }
        }).start();
    }

    void bitmapSaved(String path, String mimeType) {
        if (!appVisible) return;
        if (path==null) {
            Toast.makeText(getApplicationContext(), getString(R.string.errorSavingPicture), Toast.LENGTH_SHORT).show();
        }
        else {
            ViewImageActivity.startActivityWithImageURI(this, Uri.fromFile(new File(path)), mimeType);
        }
    }

    void updateColorButton() {
        try {
            String resName = "btn_color_" + this.colorType.name().toLowerCase();
            Integer resId = (Integer)R.drawable.class.getField(resName).get(null);
            cycleColorButton.setImageResource(resId);
        }
        catch(Exception ex) {
            Log.e(TAG, "Error updating color button", ex);
        }
    }

    // onClick_ methods are assigned as onclick handlers in the main.xml layout
    public void onClick_cycleColorMode(View view) {
        AsciiConverter.ColorType[] colorTypeValues = AsciiConverter.ColorType.values();
        this.colorType = colorTypeValues[(this.colorType.ordinal() + 1) % colorTypeValues.length];
        saveColorStyleToPreferences();
        updateColorButton();
    }

    public void onClick_gotoGallery(View view) {
        Intent intent = LibraryActivity.intentWithImageDirectory(this,
                imageWriter.getBasePictureDirectory(), imageWriter.getThumbnailDirectory());
        startActivity(intent);
    }

    public void onClick_gotoAbout(View view) {
        AboutActivity.startIntent(this);
    }

    public void onClick_gotoPreferences(View view) {
        AsciiCamPreferences.startIntent(this, ACTIVITY_PREFERENCES);
    }

    public void onClick_switchCamera(View view) {
        arManager.switchToNextCamera();
    }

    public void onClick_convertPicture(View view) {
        Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(i, ACTIVITY_PICK_IMAGE);
    }

    // Creates the bitmap to display from a camera preview frame.
    AsyncProcessor.Producer<CameraPreviewData, Bitmap> asciiProducer =
            new AsyncProcessor.Producer<CameraPreviewData, Bitmap>() {
        @Override public Bitmap processInput(CameraPreviewData input) {
            AsciiConverter.Orientation orientation = input.cameraInfo.isRotated180Degrees() ?
                    AsciiConverter.Orientation.ROTATED_180 : AsciiConverter.Orientation.NORMAL;

            synchronized (asciiResult) {
                imageRenderer.setMaximumImageSize(overlayView.getWidth(), overlayView.getHeight());
                imageRenderer.setCameraImageSize(input.width, input.height);
                asciiConverter.computeResultForCameraData(input.pixelData, input.width, input.height,
                        imageRenderer.asciiRows(), imageRenderer.asciiColumns(),
                        colorType, pixelCharsMap.get(colorType), orientation, asciiResult);
                return imageRenderer.createBitmap(asciiResult);
            }
        }
    };

    // Callback to display the produced Bitmap.
    AsyncProcessor.SuccessCallback<CameraPreviewData, Bitmap> successCallback =
            new AsyncProcessor.SuccessCallback<CameraPreviewData, Bitmap>() {
        @Override public void handleResult(CameraPreviewData input, Bitmap output) {
            overlayView.setFlipHorizontal(input.cameraInfo.isFrontFacing());
            overlayView.setBitmap(output);
            overlayView.invalidate();
            if (DEBUG) {
                long processingMillis = System.currentTimeMillis() - input.timestamp;
                Log.i("AsciiCam", "Processed frame in : " + processingMillis + "ms");
            }
            finishFrame(input);
        }
    };

    // Callback to handle an error producing a Bitmap.
    AsyncProcessor.ErrorCallback<CameraPreviewData> errorCallback =
            new AsyncProcessor.ErrorCallback<CameraPreviewData>() {
        @Override public void handleException(CameraPreviewData input, Exception ex) {
            Log.e(TAG, "Exception creating ascii image", ex);
            finishFrame(input);
        }
    };

    void finishFrame(CameraPreviewData previewData) {
        CameraUtils.addPreviewCallbackBuffer(previewData.camera, previewData.pixelData);
        if (imageProcessor != null && nextPreviewData != null) {
            if (DEBUG) Log.i(TAG, "Processing previously queued data");
            imageProcessor.processInputAsync(asciiProducer, nextPreviewData, successCallback, errorCallback, handler);
            nextPreviewData = null;
        }
    }

    @Override public void onPreviewFrame(byte[] data, Camera camera) {
        if (saveInProgress) {
            CameraUtils.addPreviewCallbackBuffer(camera, data);
            return;
        }
        Camera.Size size = camera.getParameters().getPreviewSize();
        CameraPreviewData previewData = new CameraPreviewData(
                camera, CameraUtils.getCameraInfo(arManager.getCameraId()), data,
                size.width, size.height,
                System.currentTimeMillis());
        if (imageProcessor.getStatus() == AsyncProcessor.Status.IDLE) {
            imageProcessor.processInputAsync(asciiProducer, previewData, successCallback, errorCallback, handler);
        }
        else {
            if (DEBUG) Log.i(TAG, "AsyncProcessor is busy, queueing data");
            if (nextPreviewData != null) {
                // This will normally only happen if there are at least 3 preview buffers: one for
                // the image currently being processed, one held in nextPreviewData, and the third
                // incoming buffer. With only 2 buffers, we'll stop receiving previews until the
                // current image is completed and its buffer is released.
                if (DEBUG) Log.i(TAG, "Replacing previous data");
                CameraUtils.addPreviewCallbackBuffer(nextPreviewData.camera, nextPreviewData.pixelData);
            }
            nextPreviewData = previewData;
        }
    }

    @Override public void onShutterButtonFocus(boolean pressed) {
        shutterButton.setImageResource(pressed ? R.drawable.btn_camera_shutter_pressed_holo :
            R.drawable.btn_camera_shutter_holo);
    }

    @Override public void onShutterButtonClick() {
        takePicture();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        // take picture when pushing hardware camera button or trackball center
        if ((keyCode==KeyEvent.KEYCODE_CAMERA || keyCode==KeyEvent.KEYCODE_DPAD_CENTER) && event.getRepeatCount()==0) {
            handler.post(new Runnable() {
                @Override public void run() {
                    takePicture();
                }
            });
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
