package com.limelight.grid.assets;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.limelight.LimeLog;
import com.limelight.utils.CacheHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DiskAssetLoader {
    // 5 MB
    private static final long MAX_ASSET_SIZE = 5 * 1024 * 1024;

    // Standard box art is 300x400
    private static final int STANDARD_ASSET_WIDTH = 300;
    private static final int STANDARD_ASSET_HEIGHT = 400;

    private final File cacheDir;

    public DiskAssetLoader(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    public boolean checkCacheExists(CachedAppAssetLoader.LoaderTuple tuple) {
        return CacheHelper.cacheFileExists(cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png");
    }

    // https://developer.android.com/topic/performance/graphics/load-bitmap.html
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculates the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public Bitmap loadBitmapFromCache(CachedAppAssetLoader.LoaderTuple tuple, int sampleSize) {
        File file = CacheHelper.openPath(false, cacheDir, "boxart", tuple.computer.uuid.toString(), tuple.app.getAppId() + ".png");

        // Don't bother with anything if it doesn't exist
        if (!file.exists()) {
            return null;
        }

        // Make sure the cached asset doesn't exceed the maximum size
        if (file.length() > MAX_ASSET_SIZE) {
            LimeLog.warning("Removing cached tuple exceeding size threshold: "+tuple);
            file.delete();
            return null;
        }

        // Lookup bounds of the downloaded image
        BitmapFactory.Options decodeOnlyOptions = new BitmapFactory.Options();
        decodeOnlyOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), decodeOnlyOptions);
        if (decodeOnlyOptions.outWidth <= 0 || decodeOnlyOptions.outHeight <= 0) {
            // Dimensions set to -1 on error. Return value always null.
            return null;
        }

        LimeLog.info("Tuple "+tuple+" has cached art of size: "+decodeOnlyOptions.outWidth+"x"+decodeOnlyOptions.outHeight);

        // Load the image scaled to the appropriate size
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(decodeOnlyOptions,
                STANDARD_ASSET_WIDTH / sampleSize,
                STANDARD_ASSET_HEIGHT / sampleSize);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = true;
        Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        if (bmp != null) {
            LimeLog.info("Tuple "+tuple+" decoded from disk cache with sample size: "+options.inSampleSize);
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
