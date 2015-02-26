package com.limelight;

import java.io.FileNotFoundException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.AppGridAdapter;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.GfeHttpResponseException;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.AdapterFragment;
import com.limelight.ui.AdapterFragmentCallbacks;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class AppView extends Activity implements AdapterFragmentCallbacks {
    private AppGridAdapter appGridAdapter;
    private String uuidString;

    private ComputerDetails computer;
    private ComputerManagerService.ApplistPoller poller;
    private SpinnerDialog blockingLoadSpinner;
    private String lastRawApplist;

    private int consecutiveAppListFailures = 0;
    private final static int CONSECUTIVE_FAILURE_LIMIT = 3;

    private final static int START_OR_RESUME_ID = 1;
    private final static int QUIT_ID = 2;
    private final static int CANCEL_ID = 3;
    private final static int START_WTIH_QUIT = 4;

    public final static String NAME_EXTRA = "Name";
    public final static String UUID_EXTRA = "UUID";

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

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Get the computer object
                    computer = managerBinder.getComputer(UUID.fromString(uuidString));

                    try {
                        appGridAdapter = new AppGridAdapter(AppView.this,
                                PreferenceConfiguration.readPreferences(AppView.this).listMode,
                                PreferenceConfiguration.readPreferences(AppView.this).smallIconMode,
                                computer, managerBinder.getUniqueId());
                    } catch (Exception e) {
                        e.printStackTrace();
                        finish();
                        return;
                    }

                    // Start updates
                    startComputerUpdates();

                    // Load the app grid with cached data (if possible)
                    populateAppGridWithCache();

                    getFragmentManager().beginTransaction()
                            .replace(R.id.appFragmentContainer, new AdapterFragment())
                            .commitAllowingStateLoss();
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    private InetAddress getAddress() {
        return computer.reachability == ComputerDetails.Reachability.LOCAL ?
        computer.localIp : computer.remoteIp;
    }

    private void startComputerUpdates() {
        if (managerBinder == null) {
            return;
        }

        managerBinder.startPolling(new ComputerManagerListener() {
            @Override
            public void notifyComputerUpdated(ComputerDetails details) {
                // Don't care about other computers
                if (!details.uuid.toString().equalsIgnoreCase(uuidString)) {
                    return;
                }

                if (details.state != ComputerDetails.State.ONLINE) {
                    consecutiveAppListFailures++;

                    if (consecutiveAppListFailures >= CONSECUTIVE_FAILURE_LIMIT) {
                        // The PC is unreachable now
                        AppView.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Display a toast to the user and quit the activity
                                Toast.makeText(AppView.this, getResources().getText(R.string.lost_connection), Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                    }

                    return;
                }

                consecutiveAppListFailures = 0;

                // App list is the same or empty; nothing to do
                if (details.rawAppList == null || details.rawAppList.equals(lastRawApplist)) {
                    return;
                }

                try {
                    lastRawApplist = details.rawAppList;
                    updateUiWithAppList(NvHTTP.getAppListByReader(new StringReader(details.rawAppList)));

                    if (blockingLoadSpinner != null) {
                        blockingLoadSpinner.dismiss();
                        blockingLoadSpinner = null;
                    }
                } catch (Exception ignored) {}
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
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String locale = PreferenceConfiguration.readPreferences(this).language;
        if (!locale.equals(PreferenceConfiguration.DEFAULT_LANGUAGE)) {
            Configuration config = new Configuration(getResources().getConfiguration());
            config.locale = new Locale(locale);
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        }

        setContentView(R.layout.activity_app_view);

        UiHelper.notifyNewRootView(this);

        uuidString = getIntent().getStringExtra(UUID_EXTRA);

        String labelText = getResources().getString(R.string.title_applist)+" "+getIntent().getStringExtra(NAME_EXTRA);
        TextView label = (TextView) findViewById(R.id.appListText);
        setTitle(labelText);
        label.setText(labelText);

        // Bind to the computer manager service
        bindService(new Intent(this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
    }

    private void populateAppGridWithCache() {
        try {
            // Try to load from cache
            lastRawApplist = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuidString));
            List<NvApp> applist = NvHTTP.getAppListByReader(new StringReader(lastRawApplist));
            updateUiWithAppList(applist);
            LimeLog.info("Loaded applist from cache");
        } catch (Exception e) {
            if (lastRawApplist != null) {
                LimeLog.warning("Saved applist corrupted: "+lastRawApplist);
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
    }

    @Override
    protected void onResume() {
        super.onResume();

        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopComputerUpdates();
    }

    private int getRunningAppId() {
        int runningAppId = -1;
        for (int i = 0; i < appGridAdapter.getCount(); i++) {
            AppObject app = (AppObject) appGridAdapter.getItem(i);
            if (app.app.getIsRunning()) {
                runningAppId = app.app.getAppId();
                break;
            }
        }
        return runningAppId;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        AppObject selectedApp = (AppObject) appGridAdapter.getItem(info.position);
        int runningAppId = getRunningAppId();
        if (runningAppId != -1) {
            if (runningAppId == selectedApp.app.getAppId()) {
                menu.add(Menu.NONE, START_OR_RESUME_ID, 1, getResources().getString(R.string.applist_menu_resume));
                menu.add(Menu.NONE, QUIT_ID, 2, getResources().getString(R.string.applist_menu_quit));
            }
            else {
                menu.add(Menu.NONE, START_WTIH_QUIT, 1, getResources().getString(R.string.applist_menu_quit_and_start));
                menu.add(Menu.NONE, CANCEL_ID, 2, getResources().getString(R.string.applist_menu_cancel));
            }
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
    }

    private void displayQuitConfirmationDialog(final Runnable onYes, final Runnable onNo) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        if (onYes != null) {
                            onYes.run();
                        }
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        if (onNo != null) {
                            onNo.run();
                        }
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getResources().getString(R.string.applist_quit_confirmation))
                .setPositiveButton(getResources().getString(R.string.yes), dialogClickListener)
                .setNegativeButton(getResources().getString(R.string.no), dialogClickListener)
                .show();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        final AppObject app = (AppObject) appGridAdapter.getItem(info.position);
        switch (item.getItemId()) {
            case START_WTIH_QUIT:
                // Display a confirmation dialog first
                displayQuitConfirmationDialog(new Runnable() {
                    @Override
                    public void run() {
                        doStart(app.app);
                    }
                }, null);
                return true;

            case START_OR_RESUME_ID:
                // Resume is the same as start for us
                doStart(app.app);
                return true;

            case QUIT_ID:
                // Display a confirmation dialog first
                displayQuitConfirmationDialog(new Runnable() {
                    @Override
                    public void run() {
                        doQuit(app.app);
                    }
                }, null);
                return true;

            case CANCEL_ID:
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    private void updateUiWithAppList(final List<NvApp> appList) {
        AppView.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                // First handle app updates and additions
                for (NvApp app : appList) {
                    boolean foundExistingApp = false;

                    // Try to update an existing app in the list first
                    for (int i = 0; i < appGridAdapter.getCount(); i++) {
                        AppObject existingApp = (AppObject) appGridAdapter.getItem(i);
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            // Found the app; update its properties
                            if (existingApp.app.getIsRunning() != app.getIsRunning()) {
                                existingApp.app.setIsRunningBoolean(app.getIsRunning());
                                updated = true;
                            }
                            if (!existingApp.app.getAppName().equals(app.getAppName())) {
                                existingApp.app.setAppName(app.getAppName());
                                updated = true;
                            }

                            foundExistingApp = true;
                            break;
                        }
                    }

                    if (!foundExistingApp) {
                        // This app must be new
                        appGridAdapter.addApp(new AppObject(app));
                        updated = true;
                    }
                }

                // Next handle app removals
                for (int i = 0; i < appGridAdapter.getCount(); i++) {
                    boolean foundExistingApp = false;
                    AppObject existingApp = (AppObject) appGridAdapter.getItem(i);

                    // Check if this app is in the latest list
                    for (NvApp app : appList) {
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            foundExistingApp = true;
                            break;
                        }
                    }

                    // This app was removed in the latest app list
                    if (!foundExistingApp) {
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

    private void doStart(NvApp app) {
        Intent intent = new Intent(this, Game.class);
        intent.putExtra(Game.EXTRA_HOST,
                computer.reachability == ComputerDetails.Reachability.LOCAL ?
                computer.localIp.getHostAddress() : computer.remoteIp.getHostAddress());
        intent.putExtra(Game.EXTRA_APP, app.getAppName());
        intent.putExtra(Game.EXTRA_UNIQUEID, managerBinder.getUniqueId());
        intent.putExtra(Game.EXTRA_STREAMING_REMOTE,
                computer.reachability != ComputerDetails.Reachability.LOCAL);
        startActivity(intent);
    }

    private void doQuit(final NvApp app) {
        Toast.makeText(AppView.this, getResources().getString(R.string.applist_quit_app)+" "+app.getAppName()+"...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(getAddress(),
                            managerBinder.getUniqueId(), null, PlatformBinding.getCryptoProvider(AppView.this));
                    if (httpConn.quitApp()) {
                        message = getResources().getString(R.string.applist_quit_success) + " " + app.getAppName();
                    } else {
                        message = getResources().getString(R.string.applist_quit_fail) + " " + app.getAppName();
                    }
                } catch (GfeHttpResponseException e) {
                    if (e.getErrorCode() == 599) {
                        message = "This session wasn't started by this device," +
                                " so it cannot be quit. End streaming on the original " +
                                "device or the PC itself. (Error code: "+e.getErrorCode()+")";
                    }
                    else {
                        message = e.getMessage();
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (Exception e) {
                    message = e.getMessage();
                } finally {
                    // Trigger a poll immediately
                    if (poller != null) {
                        poller.pollNow();
                    }
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AppView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    @Override
    public int getAdapterFragmentLayoutId() {
        return PreferenceConfiguration.readPreferences(this).listMode ?
                R.layout.list_view : (PreferenceConfiguration.readPreferences(AppView.this).smallIconMode ?
                    R.layout.app_grid_view_small : R.layout.app_grid_view);
    }

    @Override
    public void receiveAbsListView(AbsListView listView) {
        listView.setAdapter(appGridAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
                                    long id) {
                AppObject app = (AppObject) appGridAdapter.getItem(pos);

                // Only open the context menu if something is running, otherwise start it
                if (getRunningAppId() != -1) {
                    openContextMenu(arg1);
                } else {
                    doStart(app.app);
                }
            }
        });
        registerForContextMenu(listView);
        listView.requestFocus();
    }

    public class AppObject {
        public final NvApp app;

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
