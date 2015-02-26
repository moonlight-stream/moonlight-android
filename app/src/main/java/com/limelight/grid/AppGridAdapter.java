package com.limelight.grid;

import android.app.Activity;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.widget.TextView;

import com.limelight.AppView;
import com.limelight.R;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.MemoryAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

public class AppGridAdapter extends GenericGridAdapter<AppView.AppObject> {
    private final Activity activity;

    private final CachedAppAssetLoader loader;
    private final ConcurrentHashMap<ImageView, CachedAppAssetLoader.LoaderTuple> loadingTuples = new ConcurrentHashMap<>();

    public AppGridAdapter(Activity activity, boolean listMode, boolean small, ComputerDetails computer, String uniqueId) throws KeyManagementException, NoSuchAlgorithmException {
        super(activity, listMode ? R.layout.simple_row : (small ? R.layout.app_grid_item_small : R.layout.app_grid_item), R.drawable.image_loading);

        this.activity = activity;
        this.loader = new CachedAppAssetLoader(computer, uniqueId, new NetworkAssetLoader(context),
                new MemoryAssetLoader(), new DiskAssetLoader(context.getCacheDir()));
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
        itemList.add(app);
        sortList();
    }

    public void removeApp(AppView.AppObject app) {
        itemList.remove(app);
    }

    private final CachedAppAssetLoader.LoadListener loadListener = new CachedAppAssetLoader.LoadListener() {
        @Override
        public void notifyLongLoad(Object object) {
            final ImageView view = (ImageView) object;

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    view.setImageResource(R.drawable.image_loading);
                    fadeInImage(view);
                }
            });
        }

        @Override
        public void notifyLoadComplete(Object object, final Bitmap bitmap) {
            final ImageView view = (ImageView) object;

            loadingTuples.remove(view);

            // Just leave the loading icon in place
            if (bitmap == null) {
                return;
            }

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    view.setImageBitmap(bitmap);
                    fadeInImage(view);
                }
            });
        }
    };

    public boolean populateImageView(final ImageView imgView, final AppView.AppObject obj) {
        // Cancel pending loads on this image view
        CachedAppAssetLoader.LoaderTuple tuple = loadingTuples.remove(imgView);
        if (tuple != null) {
            // FIXME: There's a small chance that this can race if we've already gone down
            // the path to notification but haven't been notified yet
            tuple.cancel();
        }

        // Clear existing contents of the image view
        imgView.setAlpha(0.0f);

        // Start loading the bitmap
        tuple = loader.loadBitmapWithContext(obj.app, imgView, loadListener);
        if (tuple != null) {
            // The load was issued asynchronously
            loadingTuples.put(imgView, tuple);
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
