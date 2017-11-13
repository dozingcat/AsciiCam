package com.dozingcatsoftware.util;

import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.util.Log;

/**
 * Processes inputs on a separate thread and makes callbacks with the results.
 */
public class AsyncProcessor<IN, OUT>  {

    private final static boolean DEBUG = false;
    private static final String TAG = "AsyncProcessor";

    public enum Status {
        NOT_STARTED,
        IDLE,
        BUSY,
        STOPPED,
    }

    public interface Producer<IN, OUT> {
        OUT processInput(IN input);
    }
    public interface SuccessCallback<IN, OUT> {
        void handleResult(IN input, OUT output);
    }
    public interface ErrorCallback<IN> {
        void handleException(IN input, Exception exception);
    }

    private volatile Status status;
    private Thread thread;
    private Handler handler;
    private Looper looper;

    volatile boolean isBusy = false;

    public AsyncProcessor() {
        status = Status.NOT_STARTED;
    }

    public Status getStatus() {
        return status;
    }

    public void start() {
        if (status != Status.NOT_STARTED) {
            throw new IllegalStateException("Can't start AsyncProcessor, its status is " + status);
        }
        thread = new Thread() {
            @Override public void run() {
                Looper.prepare();
                looper = Looper.myLooper();
                if (DEBUG) Log.i("AsyncProcessor", "Looper: " + looper);
                handler = new Handler();
                // FIXME: This never gets called, so status stays BUSY.
                Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
                    @Override public boolean queueIdle() {
                        if (DEBUG) Log.i(TAG, "AsyncProcessor got queueIdle notification, status=" + status);
                        if (status == Status.BUSY) {
                            status = Status.IDLE;
                        }
                        return true;
                    }
                });
                Looper.loop();
                if (DEBUG) Log.i("Looper", "Exiting looper thread");
            }
        };
        thread.start();
        status = Status.IDLE;
    }

    /**
     * Calls the {@code producer} function on {@code input}. If successful, invokes
     * {@code successCallback} on {@code callbackHandler}, which is normally owned by the thread
     * that called this method. If an exception occurs, invokes {@code errorCallback} instead.
     *
     * If called when this thread is already executing another request, then this request will be
     * queued and executed first-in first-out.
     */
    public void processInputAsync(
            final Producer<IN, OUT> producer, final IN input,
            final SuccessCallback<IN, OUT> successCallback, final ErrorCallback<IN> errorCallback,
            final Handler callbackHandler) {
        if (status != Status.IDLE && status != Status.BUSY) {
            throw new IllegalStateException("Can't process new message, status is " + status);
        }
        status = Status.BUSY;
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    if (DEBUG) Log.i(TAG, "Calling processInput on AsyncProcessor thread");
                    final OUT result = producer.processInput(input);
                    if (successCallback != null) {
                        if (DEBUG) Log.i(TAG, "Produced output, calling success callback");
                        callbackHandler.post(new Runnable() {
                            @Override public void run() {
                                successCallback.handleResult(input, result);
                            }
                        });
                    }
                }
                catch (final Exception ex) {
                    Log.e(TAG, "Exception producing output", ex);
                    if (errorCallback != null) {
                        callbackHandler.post(new Runnable() {
                            @Override public void run() {
                                errorCallback.handleException(input, ex);
                            }
                        });
                    }
                }
            }
        });
    }

    public void stop() {
        looper.quit();
        status = Status.STOPPED;
    }
}
