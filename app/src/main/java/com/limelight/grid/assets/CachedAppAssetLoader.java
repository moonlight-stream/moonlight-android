package com.limelight.grid.assets;

import android.graphics.Bitmap;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;

import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CachedAppAssetLoader {
    private final ComputerDetails computer;
    private final double scalingDivider;
    private final ThreadPoolExecutor foregroundExecutor = new ThreadPoolExecutor(8, 8, Long.MAX_VALUE, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>());
    private final ThreadPoolExecutor backgroundExecutor = new ThreadPoolExecutor(2, 2, Long.MAX_VALUE, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>());
    private final NetworkAssetLoader networkLoader;
    private final MemoryAssetLoader memoryLoader;
    private final DiskAssetLoader diskLoader;

    public CachedAppAssetLoader(ComputerDetails computer, double scalingDivider,
                                NetworkAssetLoader networkLoader, MemoryAssetLoader memoryLoader,
                                DiskAssetLoader diskLoader) {
        this.computer = computer;
        this.scalingDivider = scalingDivider;

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

                Bitmap bmp = diskLoader.loadBitmapFromCache(tuple, (int) scalingDivider);
                if (bmp == null) {
                    // Notify the listener that this may take a while
                    listener.notifyLongLoad(context);

                    // Try 5 times maximum
                    for (int i = 0; i < 5; i++) {
                        // Check again whether we've been cancelled
                        if (tuple.cancelled) {
                            return;
                        }

                        InputStream in = networkLoader.getBitmapStream(tuple);
                        if (in != null) {
                            // Write the stream straight to disk
                            diskLoader.populateCacheWithStream(tuple, in);

                            // Read it back scaled
                            bmp = diskLoader.loadBitmapFromCache(tuple, (int) scalingDivider);
                            if (bmp != null) {
                                break;
                            }
                        }

                        // Wait 1 second with a bit of fuzz
                        try {
                            Thread.sleep((int) (1000 + (Math.random() * 500)));
                        } catch (InterruptedException e) {
                            break;
                        }
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
        return loadBitmapWithContext(app, context, listener, false);
    }

    public LoaderTuple loadBitmapWithContextInBackground(NvApp app, Object context, LoadListener listener) {
        return loadBitmapWithContext(app, context, listener, true);
    }

    private LoaderTuple loadBitmapWithContext(NvApp app, Object context, LoadListener listener, boolean background) {
        LoaderTuple tuple = new LoaderTuple(computer, app);

        // First, try the memory cache in the current context
        Bitmap bmp = memoryLoader.loadBitmapFromCache(tuple);
        if (bmp != null) {
            // The caller never sees our tuple in this case
            listener.notifyLoadComplete(context, bmp);
            return null;
        }

        // If it's not in memory, throw this in our executor
        if (background) {
            backgroundExecutor.execute(createLoaderRunnable(tuple, context, listener));
        }
        else {
            foregroundExecutor.execute(createLoaderRunnable(tuple, context, listener));
        }
        return tuple;
    }

    public class LoaderTuple {
        public final ComputerDetails computer;
        public final NvApp app;

        public boolean notified;
        public boolean cancelled;

        public LoaderTuple(ComputerDetails computer, NvApp app) {
            this.computer = computer;
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

    public interface LoadListener {
        // Notifies that the load didn't hit any cache and is about to be dispatched
        // over the network
        public void notifyLongLoad(Object context);

        // Bitmap may be null if the load failed
        public void notifyLoadComplete(Object context, Bitmap bitmap);
    }
}
