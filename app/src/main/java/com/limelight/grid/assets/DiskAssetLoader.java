package com.limelight.grid.assets;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.limelight.LimeLog;
import com.limelight.utils.CacheHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DiskAssetLoader {
    // 5 MB
    private final long MAX_ASSET_SIZE = 5 * 1024 * 1024;

    private final File cacheDir;

    public DiskAssetLoader(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    public boolean checkCacheExists(CachedAppAssetLoader.LoaderTuple tuple) {
        return CacheHelper.cacheFileExists(cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png");
    }

    public Bitmap loadBitmapFromCache(CachedAppAssetLoader.LoaderTuple tuple, int sampleSize) {
        InputStream in = null;
        Bitmap bmp = null;
        try {
            // Make sure the cached asset doesn't exceed the maximum size
            if (CacheHelper.getFileSize(cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png") > MAX_ASSET_SIZE) {
                LimeLog.warning("Removing cached tuple exceeding size threshold: "+tuple);
                CacheHelper.deleteCacheFile(cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png");
                return null;
            }

            in = CacheHelper.openCacheFileForInput(cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            bmp = BitmapFactory.decodeStream(in, null, options);
        } catch (IOException ignored) {
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
        }

        if (bmp != null) {
            LimeLog.info("Disk cache hit for tuple: "+tuple);
        }

        return bmp;
    }

    public void populateCacheWithStream(CachedAppAssetLoader.LoaderTuple tuple, InputStream input) {
        OutputStream out = null;
        boolean success = false;
        try {
            out = CacheHelper.openCacheFileForOutput(cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png");
            CacheHelper.writeInputStreamToOutputStream(input, out, MAX_ASSET_SIZE);
            success = true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {}
            }

            if (!success) {
                LimeLog.warning("Unable to populate cache with tuple: "+tuple);
                CacheHelper.deleteCacheFile(cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png");
            }
        }
    }
}
