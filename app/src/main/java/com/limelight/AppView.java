package com.limelight;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.AppGridAdapter;
import com.limelight.grid.GameShelfAdapter;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.console.AmbientBackgroundView;
import com.limelight.ui.console.ConsoleActionPanel;
import com.limelight.ui.console.LauncherBackdropController;
import com.limelight.ui.console.LauncherLibraryStore;
import com.limelight.ui.console.LauncherUiPreferences;
import com.limelight.ui.console.LibraryShelfProjector;
import com.limelight.ui.console.UiFeedbackManager;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.xmlpull.v1.XmlPullParserException;

public class AppView extends Activity {
    private AppGridAdapter appGridAdapter;
    private GameShelfAdapter allGamesAdapter;
    private RecyclerView allGamesGrid;
    private GridLayoutManager gameGridLayoutManager;
    private View gameGridPage;
    private View runningGamePage;
    private View runningGameCard;
    private ImageView runningGameImage;
    private TextView runningGameFallbackTitle;
    private TextView runningGameFocusTitle;
    private TextView selectedGameTitle;
    private AppObject runningApp;
    private int gridFocusedAppId;
    private int lastGridFocusPosition;
    private boolean showingRunningPage;
    private boolean pageTransitioning;
    private float runningTouchStartY;
    private AmbientBackgroundView ambientBackground;
    private LauncherBackdropController backdropController;
    private LauncherLibraryStore libraryStore;
    private UiFeedbackManager uiFeedback;
    private AppObject contextApp;
    private View contextAppView;
    private int focusedAppId;
    private String uuidString;
    private ShortcutHelper shortcutHelper;

    private ComputerDetails computer;
    private ComputerManagerService.ApplistPoller poller;
    private SpinnerDialog blockingLoadSpinner;
    private String lastRawApplist;
    private int lastRunningAppId;
    private boolean suspendGridUpdates;
    private boolean inForeground;
    private boolean showHiddenApps;
    private HashSet<Integer> hiddenAppIds = new HashSet<>();

    private final static int START_OR_RESUME_ID = 1;
    private final static int QUIT_ID = 2;
    private final static int START_WITH_QUIT = 4;
    private final static int VIEW_DETAILS_ID = 5;
    private final static int CREATE_SHORTCUT_ID = 6;
    private final static int HIDE_APP_ID = 7;
    private final static int FAVORITE_APP_ID = 8;

    public final static String HIDDEN_APPS_PREF_FILENAME = "HiddenApps";

    public final static String NAME_EXTRA = "Name";
    public final static String UUID_EXTRA = "UUID";
    public final static String NEW_PAIR_EXTRA = "NewPair";
    public final static String SHOW_HIDDEN_APPS_EXTRA = "ShowHiddenApps";

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder)binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Get the computer object
                    computer = localBinder.getComputer(uuidString);
                    if (computer == null) {
                        finish();
                        return;
                    }

                    // Add a launcher shortcut for this PC (forced, since this is user interaction)
                    shortcutHelper.createAppViewShortcut(computer, true, getIntent().getBooleanExtra(NEW_PAIR_EXTRA, false));
                    shortcutHelper.reportComputerShortcutUsed(computer);

                    try {
                        appGridAdapter = new AppGridAdapter(AppView.this,
                                PreferenceConfiguration.readPreferences(AppView.this),
                                computer, localBinder.getUniqueId(),
                                showHiddenApps);
                    } catch (Exception e) {
                        e.printStackTrace();
                        finish();
                        return;
                    }

                    appGridAdapter.updateHiddenApps(hiddenAppIds, true);
                    runOnUiThread(() -> setupLibraryAdapters());

                    // Now make the binder visible. We must do this after appGridAdapter
                    // is set to prevent us from reaching updateUiWithServerinfo() and
                    // touching the appGridAdapter prior to initialization.
                    managerBinder = localBinder;

                    // Load the app grid with cached data (if possible).
                    // This must be done _before_ startComputerUpdates()
                    // so the initial serverinfo response can update the running
                    // icon.
                    populateAppGridWithCache();

                    // Start updates
                    startComputerUpdates();

                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // If appGridAdapter is initialized, let it know about the configuration change.
        // If not, it will pick it up when it initializes.
        if (appGridAdapter != null) {
            // Update the app grid adapter to create grid items with the correct layout
            appGridAdapter.updateLayoutWithPreferences(this, PreferenceConfiguration.readPreferences(this));

            updateGameGridSpanCount();
            refreshShelves();
        }
    }

    private void startComputerUpdates() {
        // Don't start polling if we're not bound or in the foreground
        if (managerBinder == null || !inForeground) {
            return;
        }

        managerBinder.startPolling(new ComputerManagerListener() {
            @Override
            public void notifyComputerUpdated(final ComputerDetails details) {
                // Do nothing if updates are suspended
                if (suspendGridUpdates) {
                    return;
                }

                // Don't care about other computers
                if (!details.uuid.equalsIgnoreCase(uuidString)) {
                    return;
                }

                if (details.state == ComputerDetails.State.OFFLINE) {
                    // The PC is unreachable now
                    AppView.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Display a toast to the user and quit the activity
                            Toast.makeText(AppView.this, getResources().getText(R.string.lost_connection), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // Close immediately if the PC is no longer paired
                if (details.state == ComputerDetails.State.ONLINE && details.pairState != PairingManager.PairState.PAIRED) {
                    AppView.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Disable shortcuts referencing this PC for now
                            shortcutHelper.disableComputerShortcut(details,
                                    getResources().getString(R.string.scut_not_paired));

                            // Display a toast to the user and quit the activity
                            Toast.makeText(AppView.this, getResources().getText(R.string.scut_not_paired), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // App list is the same or empty
                if (details.rawAppList == null || details.rawAppList.equals(lastRawApplist)) {

                    // Let's check if the running app ID changed
                    if (details.runningGameId != lastRunningAppId) {
                        // Update the currently running game using the app ID
                        lastRunningAppId = details.runningGameId;
                        updateUiWithServerinfo(details);
                    }

                    return;
                }

                lastRunningAppId = details.runningGameId;
                lastRawApplist = details.rawAppList;

                try {
                    updateUiWithAppList(NvHTTP.getAppListByReader(new StringReader(details.rawAppList)));
                    updateUiWithServerinfo(details);

                    if (blockingLoadSpinner != null) {
                        blockingLoadSpinner.dismiss();
                        blockingLoadSpinner = null;
                    }
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                }
            }
        });

        if (poller == null) {
            poller = managerBinder.createAppListPoller(computer);
        }
        poller.start();
    }

    private void stopComputerUpdates() {
        if (poller != null) {
            poller.stop();
        }

        if (managerBinder != null) {
            managerBinder.stopPolling();
        }

        if (appGridAdapter != null) {
            appGridAdapter.cancelQueuedOperations();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        shortcutHelper = new ShortcutHelper(this);
        libraryStore = new LauncherLibraryStore(this);
        uiFeedback = new UiFeedbackManager(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_app_view);

        // Allow floating expanded PiP overlays while browsing apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        UiHelper.notifyNewRootView(this);

        showHiddenApps = getIntent().getBooleanExtra(SHOW_HIDDEN_APPS_EXTRA, false);
        uuidString = getIntent().getStringExtra(UUID_EXTRA);

        SharedPreferences hiddenAppsPrefs = getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE);
        for (String hiddenAppIdStr : hiddenAppsPrefs.getStringSet(uuidString, new HashSet<String>())) {
            hiddenAppIds.add(Integer.parseInt(hiddenAppIdStr));
        }

        String computerName = getIntent().getStringExtra(NAME_EXTRA);

        TextView label = findViewById(R.id.appListText);
        allGamesGrid = findViewById(R.id.allGamesGrid);
        gameGridPage = findViewById(R.id.gameGridPage);
        runningGamePage = findViewById(R.id.runningGamePage);
        runningGameCard = findViewById(R.id.runningGameCard);
        runningGameImage = runningGameCard.findViewById(R.id.grid_image);
        runningGameFallbackTitle = runningGameCard.findViewById(R.id.grid_text);
        runningGameFocusTitle = runningGameCard.findViewById(R.id.grid_focus_title);
        selectedGameTitle = findViewById(R.id.selectedGameTitle);
        gameGridLayoutManager = new GridLayoutManager(this, 4);
        allGamesGrid.setLayoutManager(gameGridLayoutManager);
        allGamesGrid.setHasFixedSize(false);
        allGamesGrid.setItemAnimator(null);
        allGamesGrid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        allGamesGrid.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                 oldLeft, oldTop, oldRight, oldBottom) ->
                updateGameGridSpanCount());
        configureLibraryPageGestures();
        ambientBackground = findViewById(R.id.ambientBackground);
        backdropController = new LauncherBackdropController(this,
                findViewById(R.id.backdropFirst), findViewById(R.id.backdropSecond));
        setTitle(computerName);
        label.setText(computerName);

        // Bind to the computer manager service
        bindService(new Intent(this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
    }

    private void setupLibraryAdapters() {
        if (appGridAdapter == null || isFinishing()) {
            return;
        }

        allGamesAdapter = new GameShelfAdapter(appGridAdapter);
        GameShelfAdapter.Listener listener = new GameShelfAdapter.Listener() {
            @Override
            public void onGameClicked(AppObject app, View view) {
                activateGame(app, view);
            }

            @Override
            public void onGameLongClicked(AppObject app, View view) {
                uiFeedback.confirm(view);
                showAppContextMenu(app, view);
            }

            @Override
            public void onGameFocused(AppObject app, View view) {
                focusGame(app, view);
                int position = allGamesGrid.getChildAdapterPosition(view);
                if (position != RecyclerView.NO_POSITION) {
                    lastGridFocusPosition = position;
                    gridFocusedAppId = app.app.getAppId();
                }
            }

            @Override
            public boolean onNavigateVertical(AppObject app, View view, int direction) {
                int position = allGamesGrid.getChildAdapterPosition(view);
                return direction == KeyEvent.KEYCODE_DPAD_UP &&
                        position != RecyclerView.NO_POSITION &&
                        position < gameGridLayoutManager.getSpanCount() &&
                        runningApp != null &&
                        showRunningGamePage(true);
            }
        };
        allGamesAdapter.setListener(listener);
        allGamesGrid.setAdapter(allGamesAdapter);
        runningGameCard.setOnClickListener(view -> {
            if (runningApp != null) {
                activateGame(runningApp, view);
            }
        });
        runningGameCard.setOnLongClickListener(view -> {
            if (runningApp != null) {
                uiFeedback.confirm(view);
                showAppContextMenu(runningApp, view);
            }
            return true;
        });
        runningGameCard.setOnFocusChangeListener((view, hasFocus) -> {
            runningGameFocusTitle.setVisibility(View.GONE);
            if (hasFocus && runningApp != null) {
                focusGame(runningApp, view);
            }
        });
        runningGameCard.setOnKeyListener((view, keyCode, event) ->
                event.getAction() == KeyEvent.ACTION_DOWN &&
                        keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                        showGameGridPage(true));
        appGridAdapter.setChangeListener(this::refreshShelves);
        appGridAdapter.setArtworkLoadListener(new CachedAppAssetLoader.ArtworkLoadListener() {
            @Override
            public void onArtworkLoaded(int appId, Bitmap bitmap, boolean placeholder) {
                runOnUiThread(() -> {
                    if (appId == focusedAppId && !placeholder &&
                            LauncherUiPreferences.read(AppView.this).dynamicBackgrounds) {
                        backdropController.show(bitmap,
                                !LauncherUiPreferences.read(AppView.this).reducedMotion);
                    }
                });
            }
        });
        refreshShelves();
    }

    private void refreshShelves() {
        if (appGridAdapter == null || allGamesAdapter == null) {
            return;
        }

        List<AppObject> installedApps = appGridAdapter.getAllApps();
        Set<Integer> installed = new HashSet<>();
        for (AppObject app : installedApps) {
            installed.add(app.app.getAppId());
        }
        libraryStore.prune(uuidString, installed);
        Set<Integer> favorites = libraryStore.getFavoriteIds(uuidString);
        for (AppObject app : installedApps) {
            app.isFavorite = favorites.contains(app.app.getAppId());
        }

        LibraryShelfProjector.Result<AppObject> projection = LibraryShelfProjector.project(
                installedApps,
                value -> value.app.getAppId(),
                value -> value.app.getAppName(),
                value -> value.isRunning,
                value -> value.isHidden,
                showHiddenApps,
                favorites,
                libraryStore.getRecentAppIds(uuidString));
        List<AppObject> visibleApps = projection.allGames;

        allGamesAdapter.submitList(visibleApps);
        runningApp = null;
        for (AppObject app : visibleApps) {
            if (app.isRunning) {
                runningApp = app;
                break;
            }
        }
        bindRunningGamePage();

        if (runningApp == null && showingRunningPage) {
            showGameGridPage(false);
        }
        if (showingRunningPage && runningApp != null) {
            focusedAppId = runningApp.app.getAppId();
            updateGameHero(runningApp);
            return;
        }

        AppObject focused = findAppById(visibleApps, gridFocusedAppId);
        if (focused != null) {
            focusedAppId = focused.app.getAppId();
            updateGameHero(focused);
            return;
        }
        if (!visibleApps.isEmpty()) {
            int fallbackPosition = Math.min(lastGridFocusPosition,
                    visibleApps.size() - 1);
            AppObject fallback = visibleApps.get(Math.max(0, fallbackPosition));
            gridFocusedAppId = fallback.app.getAppId();
            focusedAppId = gridFocusedAppId;
            lastGridFocusPosition = Math.max(0, fallbackPosition);
            updateGameHero(fallback);
            if (!allGamesGrid.isInTouchMode()) {
                restoreGridFocus();
            }
        }
    }

    private static AppObject findAppById(List<AppObject> apps, int appId) {
        for (AppObject app : apps) {
            if (app.app.getAppId() == appId) {
                return app;
            }
        }
        return null;
    }

    private void activateGame(AppObject app, View view) {
        uiFeedback.confirm(view);
        if (lastRunningAppId == 0 || lastRunningAppId == app.app.getAppId()) {
            recordAndStart(app);
        }
        else {
            showAppContextMenu(app, view);
        }
    }

    private void focusGame(AppObject app, View view) {
        focusedAppId = app.app.getAppId();
        updateGameHero(app);
        uiFeedback.focus(view);
        showBackdropFromCard(view);
    }

    private void showBackdropFromCard(View card) {
        ImageView artwork = card.findViewById(R.id.grid_image);
        if (!LauncherUiPreferences.read(this).dynamicBackgrounds ||
                artwork == null || !(artwork.getDrawable() instanceof BitmapDrawable)) {
            return;
        }
        Bitmap bitmap = ((BitmapDrawable) artwork.getDrawable()).getBitmap();
        if (bitmap != null && bitmap.getWidth() > 1 && bitmap.getHeight() > 1) {
            backdropController.show(bitmap,
                    !LauncherUiPreferences.read(this).reducedMotion);
        }
    }

    private void bindRunningGamePage() {
        if (runningApp == null) {
            if (!showingRunningPage) {
                runningGamePage.setVisibility(View.GONE);
            }
            return;
        }

        String appName = runningApp.app.getAppName();
        runningGameFallbackTitle.setText(appName);
        runningGameFocusTitle.setText(appName);
        runningGameFocusTitle.setVisibility(View.GONE);
        runningGameCard.<ImageView>findViewById(R.id.grid_overlay)
                .setVisibility(View.VISIBLE);
        TextView badge = runningGameCard.findViewById(R.id.grid_badge);
        if (runningApp.isHidden) {
            badge.setText(R.string.console_hidden);
            badge.setVisibility(View.VISIBLE);
        }
        else if (runningApp.isFavorite) {
            badge.setText("★");
            badge.setVisibility(View.VISIBLE);
        }
        else {
            badge.setVisibility(View.GONE);
        }
        runningGameCard.setContentDescription(appName + ". " +
                getString(R.string.console_running));
        appGridAdapter.populateArtwork(runningApp, runningGameImage,
                runningGameFallbackTitle);
    }

    private void updateGameGridSpanCount() {
        if (allGamesGrid == null || gameGridLayoutManager == null ||
                allGamesGrid.getWidth() == 0) {
            return;
        }
        boolean compact =
                PreferenceConfiguration.readPreferences(this).smallIconMode;
        float density = getResources().getDisplayMetrics().density;
        int cardWidthDp = compact ? 112 : 150;
        int gapPx = getResources().getDimensionPixelSize(R.dimen.console_card_gap);
        int cellWidth = Math.round(cardWidthDp * density) + gapPx;
        int availableWidth = allGamesGrid.getWidth() -
                allGamesGrid.getPaddingLeft() - allGamesGrid.getPaddingRight();
        int spanCount = Math.max(2, availableWidth / Math.max(1, cellWidth));
        if (gameGridLayoutManager.getSpanCount() != spanCount) {
            gameGridLayoutManager.setSpanCount(spanCount);
        }
    }

    private void configureLibraryPageGestures() {
        final float swipeThreshold = 64 *
                getResources().getDisplayMetrics().density;
        allGamesGrid.addOnItemTouchListener(
                new RecyclerView.SimpleOnItemTouchListener() {
                    private float touchStartY;
                    private boolean trackingTop;

                    @Override
                    public boolean onInterceptTouchEvent(RecyclerView recyclerView,
                                                         MotionEvent event) {
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            touchStartY = event.getY();
                            trackingTop = !recyclerView.canScrollVertically(-1);
                        }
                        else if (event.getActionMasked() == MotionEvent.ACTION_MOVE &&
                                trackingTop && runningApp != null &&
                                event.getY() - touchStartY > swipeThreshold) {
                            showRunningGamePage(false);
                            return true;
                        }
                        return false;
                    }
                });
        allGamesGrid.setOnGenericMotionListener((view, event) ->
                event.getAction() == MotionEvent.ACTION_SCROLL &&
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0 &&
                        !allGamesGrid.canScrollVertically(-1) &&
                        showRunningGamePage(false));

        View.OnTouchListener runningPageTouchListener = (view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                runningTouchStartY = event.getY();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float travel = event.getY() - runningTouchStartY;
                if (travel < -swipeThreshold) {
                    return showGameGridPage(false);
                }
                if (view == runningGameCard &&
                        Math.abs(travel) < swipeThreshold / 3) {
                    view.performClick();
                }
                return true;
            }
            return true;
        };
        runningGamePage.setOnTouchListener(runningPageTouchListener);
        runningGameCard.setOnTouchListener(runningPageTouchListener);
        runningGamePage.setOnGenericMotionListener((view, event) ->
                event.getAction() == MotionEvent.ACTION_SCROLL &&
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0 &&
                        showGameGridPage(false));
    }

    private boolean showRunningGamePage(boolean requestFocus) {
        if (runningApp == null || showingRunningPage || pageTransitioning) {
            return runningApp != null;
        }

        View focused = allGamesGrid.getFocusedChild();
        if (focused != null) {
            int position = allGamesGrid.getChildAdapterPosition(focused);
            if (position != RecyclerView.NO_POSITION) {
                lastGridFocusPosition = position;
            }
        }
        showingRunningPage = true;
        focusedAppId = runningApp.app.getAppId();
        updateGameHero(runningApp);
        bindRunningGamePage();
        showBackdropFromCard(runningGameCard);
        animatePageChange(gameGridPage, runningGamePage, true, () -> {
            if (requestFocus) {
                runningGameCard.requestFocus();
            }
        });
        return true;
    }

    private boolean showGameGridPage(boolean requestFocus) {
        if (!showingRunningPage || pageTransitioning) {
            return !showingRunningPage;
        }

        showingRunningPage = false;
        AppObject gridApp = findAppByIdFromAdapter(gridFocusedAppId);
        if (gridApp != null) {
            focusedAppId = gridApp.app.getAppId();
            updateGameHero(gridApp);
        }
        animatePageChange(runningGamePage, gameGridPage, false, () -> {
            if (requestFocus) {
                restoreGridFocus();
            }
        });
        return true;
    }

    private void animatePageChange(View outgoing, View incoming, boolean incomingAbove,
                                   Runnable onComplete) {
        pageTransitioning = true;
        boolean reducedMotion = LauncherUiPreferences.read(this).reducedMotion;
        long duration = reducedMotion ? 100 : 260;
        int travel = Math.max(1, findViewById(R.id.libraryPager).getHeight());
        outgoing.animate().cancel();
        incoming.animate().cancel();
        incoming.setVisibility(View.VISIBLE);
        incoming.setAlpha(reducedMotion ? 0f : 1f);
        incoming.setTranslationY(reducedMotion ? 0 :
                (incomingAbove ? -travel : travel));

        incoming.animate().alpha(1f).translationY(0).setDuration(duration).start();
        outgoing.animate()
                .alpha(reducedMotion ? 0f : 1f)
                .translationY(reducedMotion ? 0 :
                        (incomingAbove ? travel : -travel))
                .setDuration(duration)
                .withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setAlpha(1f);
                    outgoing.setTranslationY(0);
                    pageTransitioning = false;
                    onComplete.run();
                })
                .start();
    }

    private AppObject findAppByIdFromAdapter(int appId) {
        if (allGamesAdapter == null) {
            return null;
        }
        for (int index = 0; index < allGamesAdapter.getItemCount(); index++) {
            AppObject app = allGamesAdapter.getItem(index);
            if (app.app.getAppId() == appId) {
                return app;
            }
        }
        return null;
    }

    private void restoreGridFocus() {
        if (allGamesAdapter == null || allGamesAdapter.getItemCount() == 0) {
            return;
        }
        int position = -1;
        for (int index = 0; index < allGamesAdapter.getItemCount(); index++) {
            if (allGamesAdapter.getItem(index).app.getAppId() == gridFocusedAppId) {
                position = index;
                break;
            }
        }
        if (position < 0) {
            position = Math.min(lastGridFocusPosition,
                    allGamesAdapter.getItemCount() - 1);
        }
        final int targetPosition = Math.max(0, position);
        allGamesGrid.scrollToPosition(targetPosition);
        allGamesGrid.post(() -> {
            RecyclerView.ViewHolder holder =
                    allGamesGrid.findViewHolderForAdapterPosition(targetPosition);
            if (holder != null) {
                holder.itemView.requestFocus();
            }
        });
    }

    private void updateGameHero(AppObject app) {
        selectedGameTitle.setText(app.app.getAppName());
        LauncherUiPreferences preferences = LauncherUiPreferences.read(this);
        long duration = preferences.reducedMotion ? 90 : 180;
        if (!preferences.dynamicBackgrounds) {
            backdropController.clear(duration != 0);
        }
    }

    private void recordAndStart(AppObject app) {
        libraryStore.recordLaunch(uuidString, app.app.getAppId(), System.currentTimeMillis());
        refreshShelves();
        ServerHelper.doStart(this, app.app, computer, managerBinder);
    }

    private void showAppContextMenu(AppObject app, View view) {
        contextApp = app;
        contextAppView = view;
        List<ConsoleActionPanel.Action> actions = new ArrayList<>();
        if (lastRunningAppId == 0) {
            actions.add(new ConsoleActionPanel.Action(START_OR_RESUME_ID,
                    getString(R.string.console_play)));
        }
        else if (lastRunningAppId == app.app.getAppId()) {
            actions.add(new ConsoleActionPanel.Action(START_OR_RESUME_ID,
                    getString(R.string.applist_menu_resume)));
            actions.add(new ConsoleActionPanel.Action(QUIT_ID,
                    getString(R.string.applist_menu_quit), true));
        }
        else {
            actions.add(new ConsoleActionPanel.Action(START_WITH_QUIT,
                    getString(R.string.applist_menu_quit_and_start)));
        }
        if (lastRunningAppId != app.app.getAppId() || app.isHidden) {
            actions.add(new ConsoleActionPanel.Action(HIDE_APP_ID,
                    getString(R.string.applist_menu_hide_app)));
        }
        actions.add(new ConsoleActionPanel.Action(FAVORITE_APP_ID, getString(
                app.isFavorite ? R.string.applist_menu_unfavorite :
                        R.string.applist_menu_favorite)));
        actions.add(new ConsoleActionPanel.Action(VIEW_DETAILS_ID,
                getString(R.string.applist_menu_details)));

        ImageView artwork = view.findViewById(R.id.grid_image);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && artwork != null &&
                artwork.getDrawable() instanceof BitmapDrawable &&
                ((BitmapDrawable) artwork.getDrawable()).getBitmap() != null) {
            actions.add(new ConsoleActionPanel.Action(CREATE_SHORTCUT_ID,
                    getString(R.string.applist_menu_scut)));
        }
        ConsoleActionPanel.show(this, app.app.getAppName(), actions,
                actionId -> performAppAction(actionId, app));
    }

    private void updateHiddenApps(boolean hideImmediately) {
        HashSet<String> hiddenAppIdStringSet = new HashSet<>();

        for (Integer hiddenAppId : hiddenAppIds) {
            hiddenAppIdStringSet.add(hiddenAppId.toString());
        }

        getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .putStringSet(uuidString, hiddenAppIdStringSet)
                .apply();

        appGridAdapter.updateHiddenApps(hiddenAppIds, hideImmediately);
    }

    private void populateAppGridWithCache() {
        try {
            // Try to load from cache
            lastRawApplist = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuidString));
            List<NvApp> applist = NvHTTP.getAppListByReader(new StringReader(lastRawApplist));
            updateUiWithAppList(applist);
            LimeLog.info("Loaded applist from cache");
        } catch (IOException | XmlPullParserException e) {
            if (lastRawApplist != null) {
                LimeLog.warning("Saved applist is corrupted");
                e.printStackTrace();
            }
            LimeLog.info("Loading applist from the network");
            // We'll need to load from the network
            loadAppsBlocking();
        }
    }

    private void loadAppsBlocking() {
        blockingLoadSpinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.applist_refresh_title),
                getResources().getString(R.string.applist_refresh_msg), true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
        if (appGridAdapter != null) {
            appGridAdapter.setArtworkLoadListener(null);
        }
        if (backdropController != null) {
            backdropController.release();
        }
        if (uiFeedback != null) {
            uiFeedback.release();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        inForeground = true;
        if (ambientBackground != null) {
            ambientBackground.resume();
        }
        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        if (ambientBackground != null) {
            ambientBackground.pause();
        }
        stopComputerUpdates();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        AppObject selectedApp;
        if (menuInfo instanceof AdapterContextMenuInfo) {
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
            selectedApp = appGridAdapter.getItem(info.position);
        }
        else if (v.getTag() instanceof AppObject) {
            selectedApp = (AppObject) v.getTag();
        }
        else {
            selectedApp = contextApp;
        }
        if (selectedApp == null) {
            return;
        }
        contextApp = selectedApp;
        contextAppView = v;

        menu.setHeaderTitle(selectedApp.app.getAppName());

        if (lastRunningAppId == 0) {
            menu.add(Menu.NONE, START_OR_RESUME_ID, 1,
                    getResources().getString(R.string.console_play));
        }
        else {
            if (lastRunningAppId == selectedApp.app.getAppId()) {
                menu.add(Menu.NONE, START_OR_RESUME_ID, 1, getResources().getString(R.string.applist_menu_resume));
                menu.add(Menu.NONE, QUIT_ID, 2, getResources().getString(R.string.applist_menu_quit));
            }
            else {
                menu.add(Menu.NONE, START_WITH_QUIT, 1, getResources().getString(R.string.applist_menu_quit_and_start));
            }
        }

        // Only show the hide checkbox if this is not the currently running app or it's already hidden
        if (lastRunningAppId != selectedApp.app.getAppId() || selectedApp.isHidden) {
            MenuItem hideAppItem = menu.add(Menu.NONE, HIDE_APP_ID, 3, getResources().getString(R.string.applist_menu_hide_app));
            hideAppItem.setCheckable(true);
            hideAppItem.setChecked(selectedApp.isHidden);
        }

        menu.add(Menu.NONE, VIEW_DETAILS_ID, 4, getResources().getString(R.string.applist_menu_details));
        menu.add(Menu.NONE, FAVORITE_APP_ID, 5, getResources().getString(
                selectedApp.isFavorite ? R.string.applist_menu_unfavorite :
                        R.string.applist_menu_favorite));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ImageView appImageView = v.findViewById(R.id.grid_image);
            if (appImageView != null) {
                if (appImageView.getDrawable() instanceof BitmapDrawable) {
                    BitmapDrawable drawable = (BitmapDrawable) appImageView.getDrawable();
                    if (drawable.getBitmap() != null) {
                        menu.add(Menu.NONE, CREATE_SHORTCUT_ID, 6,
                                getResources().getString(R.string.applist_menu_scut));
                    }
                }
            }
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final AppObject app;
        if (item.getMenuInfo() instanceof AdapterContextMenuInfo) {
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
            app = appGridAdapter.getItem(info.position);
        }
        else {
            app = contextApp;
        }
        if (app == null) {
            return super.onContextItemSelected(item);
        }
        return performAppAction(item.getItemId(), app);
    }

    private boolean performAppAction(int actionId, final AppObject app) {
        switch (actionId) {
            case START_WITH_QUIT:
                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        recordAndStart(app);
                    }
                }, null);
                return true;

            case START_OR_RESUME_ID:
                recordAndStart(app);
                return true;

            case QUIT_ID:
                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        suspendGridUpdates = true;
                        ServerHelper.doQuit(AppView.this, computer,
                                app.app, managerBinder, new Runnable() {
                            @Override
                            public void run() {
                                // Trigger a poll immediately
                                suspendGridUpdates = false;
                                if (poller != null) {
                                    poller.pollNow();
                                }
                            }
                        });
                    }
                }, null);
                return true;

            case VIEW_DETAILS_ID:
                Dialog.displayDialog(AppView.this, getResources().getString(R.string.title_details), app.app.toString(), false);
                return true;

            case HIDE_APP_ID:
                if (app.isHidden) {
                    hiddenAppIds.remove(app.app.getAppId());
                }
                else {
                    hiddenAppIds.add(app.app.getAppId());
                }
                updateHiddenApps(false);
                return true;

            case CREATE_SHORTCUT_ID:
                ImageView appImageView = contextAppView.findViewById(R.id.grid_image);
                if (!(appImageView.getDrawable() instanceof BitmapDrawable)) {
                    return true;
                }
                Bitmap appBits = ((BitmapDrawable) appImageView.getDrawable()).getBitmap();
                if (!shortcutHelper.createPinnedGameShortcut(computer, app.app, appBits)) {
                    Toast.makeText(AppView.this, getResources().getString(R.string.unable_to_pin_shortcut), Toast.LENGTH_LONG).show();
                }
                return true;

            case FAVORITE_APP_ID:
                libraryStore.toggleFavorite(uuidString, app.app.getAppId());
                refreshShelves();
                return true;

            default:
                return false;
        }
    }

    private void updateUiWithServerinfo(final ComputerDetails details) {
        AppView.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                    // Look through our current app list to tag the running app
                for (int i = 0; i < appGridAdapter.getCount(); i++) {
                    AppObject existingApp = (AppObject) appGridAdapter.getItem(i);

                    // There can only be one or zero apps running.
                    if (existingApp.isRunning &&
                            existingApp.app.getAppId() == details.runningGameId) {
                        // This app was running and still is, so we're done now
                        return;
                    }
                    else if (existingApp.app.getAppId() == details.runningGameId) {
                        // This app wasn't running but now is
                        existingApp.isRunning = true;
                        updated = true;
                    }
                    else if (existingApp.isRunning) {
                        // This app was running but now isn't
                        existingApp.isRunning = false;
                        updated = true;
                    }
                    else {
                        // This app wasn't running and still isn't
                    }
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private void updateUiWithAppList(final List<NvApp> appList) {
        AppView.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;
                Map<Integer, AppObject> existingApps = new HashMap<>();
                for (AppObject existingApp : appGridAdapter.getAllApps()) {
                    existingApps.put(existingApp.app.getAppId(), existingApp);
                }
                Set<Integer> refreshedAppIds = new HashSet<>();

                // First handle app updates and additions
                for (NvApp app : appList) {
                    refreshedAppIds.add(app.getAppId());
                    AppObject existingApp = existingApps.get(app.getAppId());
                    if (existingApp != null) {
                        if (!existingApp.app.getAppName().equals(app.getAppName())) {
                            existingApp.app.setAppName(app.getAppName());
                            updated = true;
                        }
                    }
                    else {
                        // This app must be new
                        appGridAdapter.addApp(new AppObject(app));

                        // We could have a leftover shortcut from last time this PC was paired
                        // or if this app was removed then added again. Enable those shortcuts
                        // again if present.
                        shortcutHelper.enableAppShortcut(computer, app);

                        updated = true;
                    }
                }

                // Next handle app removals
                for (AppObject existingApp : existingApps.values()) {
                    if (!refreshedAppIds.contains(existingApp.app.getAppId())) {
                        shortcutHelper.disableAppShortcut(computer, existingApp.app, "App removed from PC");
                        appGridAdapter.removeApp(existingApp);
                        updated = true;
                    }
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    public static class AppObject {
        public final NvApp app;
        public boolean isRunning;
        public boolean isHidden;
        public boolean isFavorite;

        public AppObject(NvApp app) {
            if (app == null) {
                throw new IllegalArgumentException("app must not be null");
            }
            this.app = app;
        }

        @Override
        public String toString() {
            return app.getAppName();
        }
    }
}
