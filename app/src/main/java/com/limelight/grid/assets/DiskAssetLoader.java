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
            in = CacheHelper.openCacheFileForInput(cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            bmp = BitmapFactory.decodeStream(in, null, options);
        } catch (FileNotFoundException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
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
        try {
            out = CacheHelper.openCacheFileForOutput(cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png");
            CacheHelper.writeInputStreamToOutputStream(input, out);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
