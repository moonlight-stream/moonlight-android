package com.limelight.grid.assets;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.widget.ImageView;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CachedAppAssetLoader {
    private static final int MAX_CONCURRENT_DISK_LOADS = 3;
    private static final int MAX_CONCURRENT_NETWORK_LOADS = 3;
    private static final int MAX_CONCURRENT_CACHE_LOADS = 1;

    private static final int MAX_PENDING_CACHE_LOADS = 100;
    private static final int MAX_PENDING_NETWORK_LOADS = 40;
    private static final int MAX_PENDING_DISK_LOADS = 40;

    private final ThreadPoolExecutor cacheExecutor = new ThreadPoolExecutor(
            MAX_CONCURRENT_CACHE_LOADS, MAX_CONCURRENT_CACHE_LOADS,
            Long.MAX_VALUE, TimeUnit.DAYS,
            new LinkedBlockingQueue<Runnable>(MAX_PENDING_CACHE_LOADS),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private final ThreadPoolExecutor foregroundExecutor = new ThreadPoolExecutor(
            MAX_CONCURRENT_DISK_LOADS, MAX_CONCURRENT_DISK_LOADS,
            Long.MAX_VALUE, TimeUnit.DAYS,
            new LinkedBlockingQueue<Runnable>(MAX_PENDING_DISK_LOADS),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private final ThreadPoolExecutor networkExecutor = new ThreadPoolExecutor(
            MAX_CONCURRENT_NETWORK_LOADS, MAX_CONCURRENT_NETWORK_LOADS,
            Long.MAX_VALUE, TimeUnit.DAYS,
            new LinkedBlockingQueue<Runnable>(MAX_PENDING_NETWORK_LOADS),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private final ComputerDetails computer;
    private final double scalingDivider;
    private final NetworkAssetLoader networkLoader;
    private final MemoryAssetLoader memoryLoader;
    private final DiskAssetLoader diskLoader;
    private final Bitmap placeholderBitmap;

    public CachedAppAssetLoader(ComputerDetails computer, double scalingDivider,
                                NetworkAssetLoader networkLoader, MemoryAssetLoader memoryLoader,
                                DiskAssetLoader diskLoader, Bitmap placeholderBitmap) {
        this.computer = computer;
        this.scalingDivider = scalingDivider;
        this.networkLoader = networkLoader;
        this.memoryLoader = memoryLoader;
        this.diskLoader = diskLoader;
        this.placeholderBitmap = placeholderBitmap;
    }

    public void cancelBackgroundLoads() {
        Runnable r;
        while ((r = cacheExecutor.getQueue().poll()) != null) {
            cacheExecutor.remove(r);
        }
    }

    public void cancelForegroundLoads() {
        Runnable r;

        while ((r = foregroundExecutor.getQueue().poll()) != null) {
            foregroundExecutor.remove(r);
        }

        while ((r = networkExecutor.getQueue().poll()) != null) {
            networkExecutor.remove(r);
        }
    }

    public void freeCacheMemory() {
        memoryLoader.clearCache();
    }

    private Bitmap doNetworkAssetLoad(LoaderTuple tuple, LoaderTask task) {
        // Try 3 times
        for (int i = 0; i < 3; i++) {
            // Check again whether we've been cancelled or the image view is gone
            if (task != null && (task.isCancelled() || task.imageViewRef.get() == null)) {
                return null;
            }

            InputStream in = networkLoader.getBitmapStream(tuple);
            if (in != null) {
                // Write the stream straight to disk
                diskLoader.populateCacheWithStream(tuple, in);

                // Close the network input stream
                try {
                    in.close();
                } catch (IOException ignored) {}

                // If there's a task associated with this load, we should return the bitmap
                if (task != null) {
                    return diskLoader.loadBitmapFromCache(tuple, (int) scalingDivider);
                }
                else {
                    // Otherwise it's a background load and we return nothing
                    return null;
                }
            }

            // Wait 1 second with a bit of fuzz
            try {
                Thread.sleep((int) (1000 + (Math.random() * 500)));
            } catch (InterruptedException e) {
                return null;
            }
        }

        return null;
    }

    private class LoaderTask extends AsyncTask<LoaderTuple, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewRef;
        private final boolean diskOnly;

        private LoaderTuple tuple;

        public LoaderTask(ImageView imageView, boolean diskOnly) {
            this.imageViewRef = new WeakReference<ImageView>(imageView);
            this.diskOnly = diskOnly;
        }

        @Override
        protected Bitmap doInBackground(LoaderTuple... params) {
            tuple = params[0];

            // Check whether it has been cancelled or the image view is gone
            if (isCancelled() || imageViewRef.get() == null) {
                return null;
            }

            Bitmap bmp = diskLoader.loadBitmapFromCache(tuple, (int) scalingDivider);
            if (bmp == null) {
                if (!diskOnly) {
                    // Try to load the asset from the network
                    bmp = doNetworkAssetLoad(tuple, this);
                } else {
                    // Report progress to display the placeholder and spin
                    // off the network-capable task
                    publishProgress();
                }
            }

            // Cache the bitmap
            if (bmp != null) {
                memoryLoader.populateCache(tuple, bmp);
            }

            return bmp;
        }

        @Override
        protected void onProgressUpdate(Void... nothing) {
            // Do nothing if cancelled
            if (isCancelled()) {
                return;
            }

            // If the current loader task for this view isn't us, do nothing
            final ImageView imageView = imageViewRef.get();
            if (getLoaderTask(imageView) == this) {
                // Set off another loader task on the network executor
                LoaderTask task = new LoaderTask(imageView, false);
                AsyncDrawable asyncDrawable = new AsyncDrawable(imageView.getResources(), placeholderBitmap, task);
                imageView.setAlpha(1.0f);
                imageView.setImageDrawable(asyncDrawable);
                task.executeOnExecutor(networkExecutor, tuple);
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            // Do nothing if cancelled
            if (isCancelled()) {
                return;
            }

            final ImageView imageView = imageViewRef.get();
            if (getLoaderTask(imageView) == this) {
                // Set the bitmap
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }

                // Show the view
                imageView.setAlpha(1.0f);
            }
        }
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<LoaderTask> loaderTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap,
                             LoaderTask loaderTask) {
            super(res, bitmap);
            loaderTaskReference = new WeakReference<LoaderTask>(loaderTask);
        }

        public LoaderTask getLoaderTask() {
            return loaderTaskReference.get();
        }
    }

    private static LoaderTask getLoaderTask(ImageView imageView) {
        if (imageView == null) {
            return null;
        }

        final Drawable drawable = imageView.getDrawable();

        // If our drawable is in play, get the loader task
        if (drawable instanceof AsyncDrawable) {
            final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
            return asyncDrawable.getLoaderTask();
        }

        return null;
    }

    private static boolean cancelPendingLoad(LoaderTuple tuple, ImageView imageView) {
        final LoaderTask loaderTask = getLoaderTask(imageView);

        // Check if any task was pending for this image view
        if (loaderTask != null && !loaderTask.isCancelled()) {
            final LoaderTuple taskTuple = loaderTask.tuple;

            // Cancel the task if it's not already loading the same data
            if (taskTuple == null || !taskTuple.equals(tuple)) {
                loaderTask.cancel(true);
            } else {
                // It's already loading what we want
                return false;
            }
        }

        // Allow the load to proceed
        return true;
    }

    public void queueCacheLoad(NvApp app) {
        final LoaderTuple tuple = new LoaderTuple(computer, app);

        if (memoryLoader.loadBitmapFromCache(tuple) != null) {
            // It's in memory which means it must also be on disk
            return;
        }

        // Queue a fetch in the cache executor
        cacheExecutor.execute(new Runnable() {
            @Override
            public void run() {
                // Check if the image is cached on disk
                if (diskLoader.checkCacheExists(tuple)) {
                    return;
                }

                // Try to load the asset from the network and cache result on disk
                doNetworkAssetLoad(tuple, null);
            }
        });
    }

    public void populateImageView(NvApp app, ImageView view) {
        LoaderTuple tuple = new LoaderTuple(computer, app);

        // If there's already a task in progress for this view,
        // cancel it. If the task is already loading the same image,
        // we return and let that load finish.
        if (!cancelPendingLoad(tuple, view)) {
            return;
        }

        // First, try the memory cache in the current context
        Bitmap bmp = memoryLoader.loadBitmapFromCache(tuple);
        if (bmp != null) {
            // Show the bitmap immediately
            view.setAlpha(1.0f);
            view.setImageBitmap(bmp);
            return;
        }

        // If it's not in memory, create an async task to load it. This task will be attached
        // via AsyncDrawable to this view.
        final LoaderTask task = new LoaderTask(view, true);
        final AsyncDrawable asyncDrawable = new AsyncDrawable(view.getResources(), placeholderBitmap, task);
        view.setAlpha(0.0f);
        view.setImageDrawable(asyncDrawable);

        // Run the task on our foreground executor
        task.executeOnExecutor(foregroundExecutor, tuple);
    }

    public class LoaderTuple {
        public final ComputerDetails computer;
        public final NvApp app;

        public LoaderTuple(ComputerDetails computer, NvApp app) {
            this.computer = computer;
            this.app = app;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LoaderTuple)) {
                return false;
            }

            LoaderTuple other = (LoaderTuple) o;
            return computer.uuid.equals(other.computer.uuid) && app.getAppId() == other.app.getAppId();
        }

        @Override
        public String toString() {
            return "("+computer.uuid+", "+app.getAppId()+")";
        }
    }
}
