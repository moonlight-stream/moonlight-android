package com.limelight.grid.assets;

import android.graphics.Bitmap;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CachedAppAssetLoader {
    private final ComputerDetails computer;
    private final String uniqueId;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, Long.MAX_VALUE, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>());
    private final NetworkLoader networkLoader;
    private final CachedLoader memoryLoader;
    private final CachedLoader diskLoader;

    public CachedAppAssetLoader(ComputerDetails computer, String uniqueId, NetworkLoader networkLoader, CachedLoader memoryLoader, CachedLoader diskLoader) {
        this.computer = computer;
        this.uniqueId = uniqueId;

        this.networkLoader = networkLoader;
        this.memoryLoader = memoryLoader;
        this.diskLoader = diskLoader;
    }

    private Runnable createLoaderRunnable(final LoaderTuple tuple, final Object context, final LoadListener listener) {
        return new Runnable() {
            @Override
            public void run() {
                // Abort if we've been cancelled
                if (tuple.cancelled) {
                    return;
                }

                Bitmap bmp = diskLoader.loadBitmapFromCache(tuple);
                if (bmp == null) {
                    // Notify the listener that this may take a while
                    listener.notifyLongLoad(context);

                    // Try 5 times maximum
                    for (int i = 0; i < 5; i++) {
                        // Check again whether we've been cancelled
                        if (tuple.cancelled) {
                            return;
                        }

                        bmp = networkLoader.loadBitmap(tuple);
                        if (bmp != null) {
                            break;
                        }

                        // Wait 1 second with a bit of fuzz
                        try {
                            Thread.sleep((int) (1000 + (Math.random()*500)));
                        } catch (InterruptedException e) {}
                    }

                    if (bmp != null) {
                        // Populate the disk cache
                        diskLoader.populateCache(tuple, bmp);
                    }
                }

                if (bmp != null) {
                    // Populate the memory cache
                    memoryLoader.populateCache(tuple, bmp);
                }

                // Check one last time whether we've been cancelled
                synchronized (tuple) {
                    if (tuple.cancelled) {
                        return;
                    }
                    else {
                        tuple.notified = true;
                    }
                }

                // Call the load complete callback (possible with a null bitmap)
                listener.notifyLoadComplete(context, bmp);
            }
        };
    }

    public LoaderTuple loadBitmapWithContext(NvApp app, Object context, LoadListener listener) {
        LoaderTuple tuple = new LoaderTuple(computer, uniqueId, app);

        // First, try the memory cache in the current context
        Bitmap bmp = memoryLoader.loadBitmapFromCache(tuple);
        if (bmp != null) {
            synchronized (tuple) {
                if (tuple.cancelled) {
                    return null;
                }
                else {
                    tuple.notified = true;
                }
            }

            listener.notifyLoadComplete(context, bmp);
            return null;
        }

        // If it's not in memory, throw this in our executor
        executor.execute(createLoaderRunnable(tuple, context, listener));
        return tuple;
    }

    public class LoaderTuple {
        public final ComputerDetails computer;
        public final String uniqueId;
        public final NvApp app;

        public boolean notified;
        public boolean cancelled;

        public LoaderTuple(ComputerDetails computer, String uniqueId, NvApp app) {
            this.computer = computer;
            this.uniqueId = uniqueId;
            this.app = app;
        }

        public boolean cancel() {
            synchronized (this) {
                cancelled = true;
                return !notified;
            }
        }

        @Override
        public String toString() {
            return "("+computer.uuid+", "+app.getAppId()+")";
        }
    }

    public interface NetworkLoader {
        public Bitmap loadBitmap(LoaderTuple tuple);
    }

    public interface CachedLoader {
        public Bitmap loadBitmapFromCache(LoaderTuple tuple);
        public void populateCache(LoaderTuple tuple, Bitmap bitmap);
    }

    public interface LoadListener {
        // Notifies that the load didn't hit any cache and is about to be dispatched
        // over the network
        public void notifyLongLoad(Object context);

        // Bitmap may be null if the load failed
        public void notifyLoadComplete(Object context, Bitmap bitmap);
    }
}
