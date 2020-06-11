package com.limelight.grid.assets;

import android.graphics.Bitmap;
import android.util.LruCache;

import com.limelight.LimeLog;

import java.lang.ref.WeakReference;
import java.util.HashMap;

public class MemoryAssetLoader {
    private static final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    private static final LruCache<String, Bitmap> memoryCache = new LruCache<String, Bitmap>(maxMemory / 16) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            // Sizeof returns kilobytes
            return bitmap.getByteCount() / 1024;
        }

        @Override
        protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
            super.entryRemoved(evicted, key, oldValue, newValue);

            if (evicted) {
                // Keep a weak reference around to the bitmap as long as we can
                evictionCache.put(key, new WeakReference<>(oldValue));
            }
        }
    };
    private static final HashMap<String, WeakReference<Bitmap>> evictionCache = new HashMap<>();

    private static String constructKey(CachedAppAssetLoader.LoaderTuple tuple) {
        return tuple.computer.uuid+"-"+tuple.app.getAppId();
    }

    public Bitmap loadBitmapFromCache(CachedAppAssetLoader.LoaderTuple tuple) {
        final String key = constructKey(tuple);

        Bitmap bmp = memoryCache.get(key);
        if (bmp != null) {
            LimeLog.info("LRU cache hit for tuple: "+tuple);
            return bmp;
        }

        WeakReference<Bitmap> bmpRef = evictionCache.get(key);
        if (bmpRef != null) {
            bmp = bmpRef.get();
            if (bmp != null) {
                LimeLog.info("Eviction cache hit for tuple: "+tuple);

                // Put this entry back into the LRU cache
                evictionCache.remove(key);
                memoryCache.put(key, bmp);

                return bmp;
            }
            else {
                // The data is gone, so remove the dangling WeakReference now
                evictionCache.remove(key);
            }
        }

        return null;
    }

    public void populateCache(CachedAppAssetLoader.LoaderTuple tuple, Bitmap bitmap) {
        memoryCache.put(constructKey(tuple), bitmap);
    }

    public void clearCache() {
        // We must evict first because that will push all items into the eviction cache
        memoryCache.evictAll();
        evictionCache.clear();
    }
}
