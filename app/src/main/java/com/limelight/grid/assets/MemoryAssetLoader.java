package com.limelight.grid.assets;

import android.graphics.Bitmap;
import android.util.LruCache;

import com.limelight.LimeLog;

public class MemoryAssetLoader {
    private static final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    private static final LruCache<String, Bitmap> memoryCache = new LruCache<String, Bitmap>(maxMemory / 16) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            // Sizeof returns kilobytes
            return bitmap.getByteCount() / 1024;
        }
    };

    private static String constructKey(CachedAppAssetLoader.LoaderTuple tuple) {
        return tuple.computer.uuid.toString()+"-"+tuple.app.getAppId();
    }

    public Bitmap loadBitmapFromCache(CachedAppAssetLoader.LoaderTuple tuple) {
        Bitmap bmp = memoryCache.get(constructKey(tuple));
        if (bmp != null) {
            LimeLog.info("Memory cache hit for tuple: "+tuple);
        }
        return bmp;
    }

    public void populateCache(CachedAppAssetLoader.LoaderTuple tuple, Bitmap bitmap) {
        memoryCache.put(constructKey(tuple), bitmap);
    }

    public void clearCache() {
        memoryCache.evictAll();
    }
}
