// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.util;

/**
 * Specialized wrapper around ProcessingQueue for the purpose of processing camera preview images.
 */
public class CameraPreviewProcessingQueue {
    
    public static interface Processor {
        // Called in a separate thread to process a camera preview image
        public abstract void processCameraImage(byte[] data, int width, int height);
    }
    
    static class CameraPreviewImage {
        public final byte[] data;
        public final int width;
        public final int height;
        
        public CameraPreviewImage(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }
    }
    
    ProcessingQueue<CameraPreviewImage> processingQueue = new ProcessingQueue<CameraPreviewImage>();

    /** Called by camera preview callback method when a frame is received.
     */
    public void processImageData(byte[] data, int width, int height) {
        processingQueue.queueData(new CameraPreviewImage(data, width, height));
    }
    
    public void start(final Processor imageProcessor) {
        processingQueue.start(new ProcessingQueue.Processor<CameraPreviewImage>() {
            @Override public void processData(CameraPreviewImage data) {
                imageProcessor.processCameraImage(data.data, data.width, data.height);
            }
        });
    }
    
    public void stop() {
        processingQueue.stop();
    }
    
    public void pause() {
        processingQueue.pause();
    }
    
    public void unpause() {
        processingQueue.unpause();
    }
}
