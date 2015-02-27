package com.limelight.grid;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import android.widget.TextView;

import com.limelight.AppView;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.MemoryAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;

import java.lang.ref.WeakReference;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppGridAdapter extends GenericGridAdapter<AppView.AppObject> {
    private final Activity activity;

    private static final int ART_WIDTH_PX = 300;
    private static final int SMALL_WIDTH_DP = 100;
    private static final int LARGE_WIDTH_DP = 150;

    private final CachedAppAssetLoader loader;
    private final ConcurrentHashMap<WeakReference<ImageView>, CachedAppAssetLoader.LoaderTuple> loadingTuples = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Object, CachedAppAssetLoader.LoaderTuple> backgroundLoadingTuples = new ConcurrentHashMap<>();

    public AppGridAdapter(Activity activity, boolean listMode, boolean small, ComputerDetails computer, String uniqueId) throws KeyManagementException, NoSuchAlgorithmException {
        super(activity, listMode ? R.layout.simple_row : (small ? R.layout.app_grid_item_small : R.layout.app_grid_item), R.drawable.image_loading);

        int dpi = activity.getResources().getDisplayMetrics().densityDpi;
        int dp;

        if (small) {
            dp = SMALL_WIDTH_DP;
        }
        else {
            dp = LARGE_WIDTH_DP;
        }

        double scalingDivisor = ART_WIDTH_PX / (dp * (dpi / 160));
        if (scalingDivisor < 1.0) {
            // We don't want to make them bigger before draw-time
            scalingDivisor = 1.0;
        }
        LimeLog.info("Art scaling divisor: " + scalingDivisor);

        this.activity = activity;
        this.loader = new CachedAppAssetLoader(computer, uniqueId, scalingDivisor,
                new NetworkAssetLoader(context, uniqueId),
                new MemoryAssetLoader(), new DiskAssetLoader(context.getCacheDir()));
    }

    private static void cancelTuples(ConcurrentHashMap<?, CachedAppAssetLoader.LoaderTuple> map) {
        Collection<CachedAppAssetLoader.LoaderTuple> tuples = map.values();

        for (CachedAppAssetLoader.LoaderTuple tuple : tuples) {
            tuple.cancel();
        }

        map.clear();
    }

    public void cancelQueuedOperations() {
        cancelTuples(loadingTuples);
        cancelTuples(backgroundLoadingTuples);
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<AppView.AppObject>() {
            @Override
            public int compare(AppView.AppObject lhs, AppView.AppObject rhs) {
                return lhs.app.getAppName().compareTo(rhs.app.getAppName());
            }
        });
    }

    public void addApp(AppView.AppObject app) {
        // Queue a request to fetch this bitmap in the background
        Object tupleKey = new Object();
        CachedAppAssetLoader.LoaderTuple tuple =
                loader.loadBitmapWithContextInBackground(app.app, tupleKey, backgroundLoadListener);
        if (tuple != null) {
            backgroundLoadingTuples.put(tupleKey, tuple);
        }

        itemList.add(app);
        sortList();
    }

    public void removeApp(AppView.AppObject app) {
        itemList.remove(app);
    }

    private final CachedAppAssetLoader.LoadListener imageViewLoadListener = new CachedAppAssetLoader.LoadListener() {
        @Override
        public void notifyLongLoad(Object object) {
            final WeakReference<ImageView> viewRef = (WeakReference<ImageView>) object;

            // If the view isn't there anymore, don't bother scheduling on the UI thread
            if (viewRef.get() == null) {
                return;
            }

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView view = viewRef.get();
                    if (view != null) {
                        view.setImageResource(R.drawable.image_loading);
                        fadeInImage(view);
                    }
                }
            });
        }

        @Override
        public void notifyLoadComplete(Object object, Bitmap bitmap) {
            final WeakReference<ImageView> viewRef = (WeakReference<ImageView>) object;

            loadingTuples.remove(viewRef);

            // Just leave the loading icon in place
            if (bitmap == null) {
                return;
            }

            // If the view isn't there anymore, don't bother scheduling on the UI thread
            if (viewRef.get() == null) {
                return;
            }

            final Bitmap viewBmp = bitmap;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView view = viewRef.get();
                    if (view != null) {
                        view.setImageBitmap(viewBmp);
                        fadeInImage(view);
                    }
                }
            });
        }
    };

    private final CachedAppAssetLoader.LoadListener backgroundLoadListener = new CachedAppAssetLoader.LoadListener() {
        @Override
        public void notifyLongLoad(Object object) {}

        @Override
        public void notifyLoadComplete(Object object, final Bitmap bitmap) {
            backgroundLoadingTuples.remove(object);
        }
    };

    private void reapLoaderTuples(ImageView view) {
        // Poor HashMap doesn't deserve this...
        Iterator<Map.Entry<WeakReference<ImageView>, CachedAppAssetLoader.LoaderTuple>> i = loadingTuples.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<WeakReference<ImageView>, CachedAppAssetLoader.LoaderTuple> entry = i.next();
            ImageView imageView = entry.getKey().get();

            // Remove tuples that refer to this view or no view
            if (imageView == null || imageView == view) {
                // FIXME: There's a small chance that this can race if we've already gone down
                // the path to notification but haven't been notified yet
                entry.getValue().cancel();

                // Remove it from the tuple list
                i.remove();
            }
        }
    }

    public boolean populateImageView(final ImageView imgView, final AppView.AppObject obj) {
        // Cancel pending loads on this image view
        reapLoaderTuples(imgView);

        // Clear existing contents of the image view
        imgView.setAlpha(0.0f);

        // Start loading the bitmap
        WeakReference<ImageView> viewRef = new WeakReference<>(imgView);
        CachedAppAssetLoader.LoaderTuple tuple = loader.loadBitmapWithContext(obj.app, viewRef, imageViewLoadListener);
        if (tuple != null) {
            // The load was issued asynchronously
            loadingTuples.put(viewRef, tuple);
        }
        return true;
    }

    @Override
    public boolean populateTextView(TextView txtView, AppView.AppObject obj) {
        // Select the text view so it starts marquee mode
        txtView.setSelected(true);

        // Return false to use the app's toString method
        return false;
    }

    @Override
    public boolean populateOverlayView(ImageView overlayView, AppView.AppObject obj) {
        if (obj.app.getIsRunning()) {
            // Show the play button overlay
            overlayView.setImageResource(R.drawable.play);
            return true;
        }

        // No overlay
        return false;
    }

    private static void fadeInImage(ImageView view) {
        view.animate().alpha(1.0f).setDuration(100).start();
    }
}
