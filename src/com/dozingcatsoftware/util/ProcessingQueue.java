// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.util;

/**
 * A variant of a producer-consumer queue where only a single data object is queued behind
 * the data currently being processed. The idea is that if new data arrives while previous
 * data is still waiting to be processed, we just drop that previous data.
 */
public class ProcessingQueue<T> {
    
    public static interface Processor<T> {
        void processData(T data);
    }
    
    Thread thread;
    boolean running;
    boolean paused;
    boolean waiting;
    T currentData;
    T nextData;
    Processor<T> dataProcessor;
    
    public void start(Processor<T> processor, String threadName) {
        this.dataProcessor = processor;
        if (thread==null) {
            thread = new Thread() {
                public void run() {
                    threadEntry();
                }
            };
            running = true;
            thread.start();
        }
        if (threadName!=null) thread.setName(threadName);
    }
    
    public void start(Processor<T> processor) {
        start(processor, null);
    }
    
    public void stop() {
        if (thread!=null) {
            running = false;
            synchronized(this) {
                this.notify();
            }
            try {
                thread.join();
            }
            catch(InterruptedException ie) {}
            thread = null;
        }
    }
    
    public void pause() {
        paused = true;
    }
    public void unpause() {
        paused = false;
    }
    
    public void queueData(T data) {
        // set nextPreviewImage and notify processor thread if it's waiting for it
        this.nextData = data;
        if (waiting) {
            synchronized(this) {
                this.notify();
            }
        }
    }

    void threadEntry() {
        try {
            while(running) {
                synchronized(this) {
                    // wait until nextPreviewImage is set in processImageData
                    while(this.nextData==null) {
                        waiting = true;
                        try {this.wait(250);}
                        catch(InterruptedException ie) {}
                        if (!running) return;
                    }               
                    this.currentData = this.nextData;
                    this.nextData = null;
                    waiting = false;
                }
                if (!paused) {
                    dataProcessor.processData(this.currentData);
                    this.currentData = null;
                }
            }
        }
        catch(Throwable ex) {
            android.util.Log.e("ProcessingQueue", "Exception in ProcessingQueue", ex);
        }
    }

}
