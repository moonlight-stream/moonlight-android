package com.limelight.grid.assets;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.limelight.LimeLog;
import com.limelight.utils.CacheHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DiskAssetLoader implements CachedAppAssetLoader.CachedLoader {
    private final File cacheDir;

    public DiskAssetLoader(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    @Override
    public Bitmap loadBitmapFromCache(CachedAppAssetLoader.LoaderTuple tuple) {
        InputStream in = null;
        Bitmap bmp = null;
        try {
            in = CacheHelper.openCacheFileForInput(cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png");
            bmp = BitmapFactory.decodeStream(in);
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

    @Override
    public void populateCache(CachedAppAssetLoader.LoaderTuple tuple, Bitmap bitmap) {
        try {
            // PNG ignores quality setting
            FileOutputStream out = CacheHelper.openCacheFileForOutput(cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png");
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, out);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
