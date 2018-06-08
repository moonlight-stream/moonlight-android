package com.limelight.grid;

import android.app.Activity;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.AppView;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.MemoryAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;

import java.util.Collections;
import java.util.Comparator;

@SuppressWarnings("unchecked")
public class AppGridAdapter extends GenericGridAdapter<AppView.AppObject> {
    private static final int ART_WIDTH_PX = 300;
    private static final int SMALL_WIDTH_DP = 100;
    private static final int LARGE_WIDTH_DP = 150;

    private final CachedAppAssetLoader loader;

    public AppGridAdapter(Activity activity, boolean listMode, boolean small, ComputerDetails computer, String uniqueId) {
        super(activity, listMode ? R.layout.simple_row : (small ? R.layout.app_grid_item_small : R.layout.app_grid_item));

        int dpi = activity.getResources().getDisplayMetrics().densityDpi;
        int dp;

        if (small) {
            dp = SMALL_WIDTH_DP;
        }
        else {
            dp = LARGE_WIDTH_DP;
        }

        double scalingDivisor = ART_WIDTH_PX / (dp * (dpi / 160.0));
        if (scalingDivisor < 1.0) {
            // We don't want to make them bigger before draw-time
            scalingDivisor = 1.0;
        }
        LimeLog.info("Art scaling divisor: " + scalingDivisor);

        this.loader = new CachedAppAssetLoader(computer, scalingDivisor,
                new NetworkAssetLoader(context, uniqueId),
                new MemoryAssetLoader(),
                new DiskAssetLoader(context));
    }

    public void cancelQueuedOperations() {
        loader.cancelForegroundLoads();
        loader.cancelBackgroundLoads();
        loader.freeCacheMemory();
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<AppView.AppObject>() {
            @Override
            public int compare(AppView.AppObject lhs, AppView.AppObject rhs) {
                return lhs.app.getAppName().toLowerCase().compareTo(rhs.app.getAppName().toLowerCase());
            }
        });
    }

    public void addApp(AppView.AppObject app) {
        // Queue a request to fetch this bitmap into cache
        loader.queueCacheLoad(app.app);

        // Add the app to our sorted list
        itemList.add(app);
        sortList();
    }

    public void removeApp(AppView.AppObject app) {
        itemList.remove(app);
    }

    @Override
    public boolean populateImageView(ImageView imgView, ProgressBar prgView, AppView.AppObject obj) {
        // Let the cached asset loader handle it
        loader.populateImageView(obj.app, imgView, prgView);
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
        if (obj.isRunning) {
            // Show the play button overlay
            overlayView.setImageResource(R.drawable.ic_play);
            return true;
        }

        // No overlay
        return false;
    }
}
