package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.PcGridAdapter;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.StreamSettings;
import com.limelight.ui.console.AmbientBackgroundView;
import com.limelight.ui.console.ConsoleActionPanel;
import com.limelight.ui.console.ConsoleHintBar;
import com.limelight.ui.console.ConsoleShelfView;
import com.limelight.ui.console.ConsoleStatusBar;
import com.limelight.ui.console.LauncherLibraryStore;
import com.limelight.ui.console.UiFeedbackManager;
import com.limelight.utils.Dialog;
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PcView extends Activity {
    private View noPcFoundLayout;
    private PcGridAdapter pcGridAdapter;
    private ConsoleShelfView hostShelf;
    private TextView hostHeroTitle;
    private TextView hostHeroStatus;
    private TextView batteryText;
    private AmbientBackgroundView ambientBackground;
    private ConsoleHintBar hintBar;
    private UiFeedbackManager uiFeedback;
    private ComputerObject contextComputer;
    private String focusedHostUuid;
    private ShortcutHelper shortcutHelper;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
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

                    // Start updates
                    startComputerUpdates();

                    // Force a keypair to be generated early to avoid discovery delays
                    new AndroidCryptoProvider(PcView.this).getClientCertificate();
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

        // Only reinitialize views if completeOnCreate() was called
        // before this callback. If it was not, completeOnCreate() will
        // handle initializing views with the config change accounted for.
        // This is not prone to races because both callbacks are invoked
        // in the main thread.
        if (completeOnCreateCalled) {
            // Reinitialize views just in case orientation changed
            initializeViews();
        }
    }

    private final static int PAIR_ID = 2;
    private final static int UNPAIR_ID = 3;
    private final static int WOL_ID = 4;
    private final static int DELETE_ID = 5;
    private final static int RESUME_ID = 6;
    private final static int QUIT_ID = 7;
    private final static int VIEW_DETAILS_ID = 8;
    private final static int FULL_APP_LIST_ID = 9;
    private final static int TEST_NETWORK_ID = 10;
    private final static int GAMESTREAM_EOL_ID = 11;

    private void initializeViews() {
        setContentView(R.layout.activity_pc_view);

        UiHelper.notifyNewRootView(this);

        // Allow floating expanded PiP overlays while browsing PCs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        // Set default preferences if we've never been run
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Set the correct layout for the PC grid
        pcGridAdapter.updateLayoutWithPreferences(this, PreferenceConfiguration.readPreferences(this));

        // Setup the list view
        View settingsButton = findViewById(R.id.settingsButton);
        View addComputerButton = findViewById(R.id.manuallyAddPc);
        hostShelf = findViewById(R.id.hostShelf);
        hostHeroTitle = findViewById(R.id.hostHeroTitle);
        hostHeroStatus = findViewById(R.id.hostHeroStatus);
        View hostHero = findViewById(R.id.hostHero);
        batteryText = findViewById(R.id.batteryText);
        ambientBackground = findViewById(R.id.ambientBackground);
        if (hintBar != null) {
            hintBar.unbindFromHost();
        }
        hintBar = findViewById(R.id.consoleHintBar);
        ConsoleHintBar.bindActivity(this, hintBar);
        ConsoleStatusBar.enterImmersiveMode(this);
        ConsoleStatusBar.updateBattery(this, batteryText);

        settingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                uiFeedback.confirm(v);
                startActivity(new Intent(PcView.this, StreamSettings.class));
            }
        });
        addComputerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                uiFeedback.confirm(v);
                Intent i = new Intent(PcView.this, AddComputerManually.class);
                startActivity(i);
            }
        });

        hostShelf.setAdapter(pcGridAdapter);
        hostShelf.setCenteredItemMetrics(
                getResources().getDimensionPixelSize(R.dimen.console_host_profile_item_width),
                getResources().getDimensionPixelSize(R.dimen.console_host_profile_gap));
        pcGridAdapter.setListener(new PcGridAdapter.Listener() {
            @Override
            public void onHostClicked(ComputerObject computer, View view) {
                uiFeedback.confirm(view);
                handleHostClick(computer, view);
            }

            @Override
            public void onHostLongClicked(ComputerObject computer, View view) {
                uiFeedback.confirm(view);
                showHostContextMenu(computer, view);
            }

            @Override
            public void onHostFocused(ComputerObject computer, View view) {
                focusedHostUuid = computer.details.uuid;
                updateHostHero(computer);
                uiFeedback.focus(view);
                hostShelf.centerFocusedChild(view);
            }
        });

        noPcFoundLayout = findViewById(R.id.no_pc_found_layout);
        updateHostsEmptyState(hostHero);
        pcGridAdapter.notifyDataSetChanged();
        hostShelf.post(hostShelf::refreshHorizontalCentering);
        int focusPosition = pcGridAdapter.indexOfUuid(focusedHostUuid);
        if (focusPosition >= 0) {
            updateHostHero(pcGridAdapter.getItem(focusPosition));
            hostShelf.scrollToPosition(focusPosition);
            hostShelf.post(() -> {
                if (hostShelf.getLayoutManager() != null) {
                    View item = hostShelf.getLayoutManager()
                            .findViewByPosition(focusPosition);
                    View focused = item == null ? null : item.findViewById(R.id.host_profile_avatar);
                    if (focused != null && !hostShelf.isInTouchMode()) {
                        focused.requestFocus();
                    }
                }
            });
        }
    }

    private void updateHostsEmptyState(View hostHero) {
        boolean empty = pcGridAdapter.getCount() == 0;
        if (noPcFoundLayout != null) {
            noPcFoundLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (hostShelf != null) {
            hostShelf.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
        if (hostHero != null) {
            hostHero.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        // Create a GLSurfaceView to fetch GLRenderer unless we have
        // a cached result already.
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    // Save the GLRenderer string so we don't need to do this next time
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();

                    LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            completeOnCreate();
                        }
                    });
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int i, int i1) {
                }

                @Override
                public void onDrawFrame(GL10 gl10) {
                }
            });
            setContentView(surfaceView);
        }
        else {
            LimeLog.info("Cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }
    }

    private void completeOnCreate() {
        completeOnCreateCalled = true;

        shortcutHelper = new ShortcutHelper(this);
        uiFeedback = new UiFeedbackManager(this);

        UiHelper.setLocale(this);

        // Bind to the computer manager service
        bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        pcGridAdapter = new PcGridAdapter(this, PreferenceConfiguration.readPreferences(this));

        initializeViews();
    }

    private void startComputerUpdates() {
        // Only allow polling to start if we're bound to CMS, polling is not already running,
        // and our activity is in the foreground.
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    if (!freezeUpdates) {
                        PcView.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateComputer(details);
                            }
                        });

                        // Add a launcher shortcut for this PC (off the main thread to prevent ANRs)
                        if (details.pairState == PairState.PAIRED) {
                            shortcutHelper.createAppViewShortcutForOnlineHost(details);
                        }
                    }
                }
            });
            runningPolling = true;
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (hintBar != null) {
            hintBar.observeTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (hintBar != null) {
            hintBar.observeKeyEvent(event);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (hintBar != null) {
            hintBar.unbindFromHost();
            hintBar = null;
        }
        if (managerBinder != null) {
            unbindService(serviceConnection);
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
        ConsoleStatusBar.enterImmersiveMode(this);
        ConsoleStatusBar.updateBattery(this, batteryText);
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
        stopComputerUpdates(false);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Dialog.closeDialogs();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        stopComputerUpdates(false);

        // Call superclass
        super.onCreateContextMenu(menu, v, menuInfo);
                
        ComputerObject computer;
        if (menuInfo instanceof AdapterContextMenuInfo) {
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
            computer = pcGridAdapter.getItem(info.position);
        }
        else if (v.getTag() instanceof ComputerObject) {
            computer = (ComputerObject) v.getTag();
        }
        else {
            computer = contextComputer;
        }
        if (computer == null) {
            return;
        }
        contextComputer = computer;

        // Add a header with PC status details
        menu.clearHeader();
        String headerTitle = computer.details.name + " - ";
        switch (computer.details.state)
        {
            case ONLINE:
                headerTitle += getResources().getString(R.string.pcview_menu_header_online);
                break;
            case OFFLINE:
                menu.setHeaderIcon(R.drawable.ic_pc_offline);
                headerTitle += getResources().getString(R.string.pcview_menu_header_offline);
                break;
            case UNKNOWN:
                headerTitle += getResources().getString(R.string.pcview_menu_header_unknown);
                break;
        }

        menu.setHeaderTitle(headerTitle);

        // Inflate the context menu
        if (computer.details.state == ComputerDetails.State.OFFLINE ||
            computer.details.state == ComputerDetails.State.UNKNOWN) {
            menu.add(Menu.NONE, WOL_ID, 1, getResources().getString(R.string.pcview_menu_send_wol));
            menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 2, getResources().getString(R.string.pcview_menu_eol));
        }
        else if (computer.details.pairState != PairState.PAIRED) {
            menu.add(Menu.NONE, PAIR_ID, 1, getResources().getString(R.string.pcview_menu_pair_pc));
            if (computer.details.nvidiaServer) {
                menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 2, getResources().getString(R.string.pcview_menu_eol));
            }
        }
        else {
            if (computer.details.runningGameId != 0) {
                menu.add(Menu.NONE, RESUME_ID, 1, getResources().getString(R.string.applist_menu_resume));
                menu.add(Menu.NONE, QUIT_ID, 2, getResources().getString(R.string.applist_menu_quit));
            }

            if (computer.details.nvidiaServer) {
                menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 3, getResources().getString(R.string.pcview_menu_eol));
            }

            menu.add(Menu.NONE, FULL_APP_LIST_ID, 4, getResources().getString(R.string.pcview_menu_app_list));
        }

        menu.add(Menu.NONE, TEST_NETWORK_ID, 5, getResources().getString(R.string.pcview_menu_test_network));
        menu.add(Menu.NONE, DELETE_ID, 6, getResources().getString(R.string.pcview_menu_delete_pc));
        menu.add(Menu.NONE, VIEW_DETAILS_ID, 7,  getResources().getString(R.string.pcview_menu_details));
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        // For some reason, this gets called again _after_ onPause() is called on this activity.
        // startComputerUpdates() manages this and won't actual start polling until the activity
        // returns to the foreground.
        startComputerUpdates();
    }

    private void doPair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.pairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                boolean success = false;
                try {
                    // Stop updates and wait while pairing
                    stopComputerUpdates(true);

                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        // Don't display any toast, but open the app list
                        message = null;
                        success = true;
                    }
                    else {
                        final String pinStr = PairingManager.generatePinString();

                        // Spin the dialog off in a thread because it blocks
                        Dialog.displayDialog(PcView.this, getResources().getString(R.string.pair_pairing_title),
                                getResources().getString(R.string.pair_pairing_msg)+" "+pinStr+"\n\n"+
                                getResources().getString(R.string.pair_pairing_help), false);

                        PairingManager pm = httpConn.getPairingManager();

                        PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr);
                        if (pairState == PairState.PIN_WRONG) {
                            message = getResources().getString(R.string.pair_incorrect_pin);
                        }
                        else if (pairState == PairState.FAILED) {
                            if (computer.runningGameId != 0) {
                                message = getResources().getString(R.string.pair_pc_ingame);
                            }
                            else {
                                message = getResources().getString(R.string.pair_fail);
                            }
                        }
                        else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                            message = getResources().getString(R.string.pair_already_in_progress);
                        }
                        else if (pairState == PairState.PAIRED) {
                            // Just navigate to the app view without displaying a toast
                            message = null;
                            success = true;

                            // Pin this certificate for later HTTPS use
                            managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();

                            // Invalidate reachability information after pairing to force
                            // a refresh before reading pair state again
                            managerBinder.invalidateStateForComputer(computer.uuid);
                        }
                        else {
                            // Should be no other values
                            message = null;
                        }
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    message = e.getMessage();
                }

                Dialog.closeDialogs();

                final String toastMessage = message;
                final boolean toastSuccess = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (toastMessage != null) {
                            Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                        }

                        if (toastSuccess) {
                            // Open the app list after a successful pairing attempt
                            doAppList(computer, true, false);
                        }
                        else {
                            // Start polling again if we're still in the foreground
                            startComputerUpdates();
                        }
                    }
                });
            }
        }).start();
    }

    private void doWakeOnLan(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.ONLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_pc_online), Toast.LENGTH_SHORT).show();
            return;
        }

        if (computer.macAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_no_mac), Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    WakeOnLanSender.sendWolPacket(computer);
                    message = getResources().getString(R.string.wol_waking_msg);
                } catch (IOException e) {
                    message = getResources().getString(R.string.wol_fail);
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doUnpair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.unpairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
                        httpConn.unpair();
                        if (httpConn.getPairState() == PairingManager.PairState.NOT_PAIRED) {
                            message = getResources().getString(R.string.unpair_success);
                        }
                        else {
                            message = getResources().getString(R.string.unpair_fail);
                        }
                    }
                    else {
                        message = getResources().getString(R.string.unpair_error);
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Intent i = new Intent(this, AppView.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired);
        i.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        startActivity(i);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final ComputerObject computer;
        if (item.getMenuInfo() instanceof AdapterContextMenuInfo) {
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
            computer = pcGridAdapter.getItem(info.position);
        }
        else {
            computer = contextComputer;
        }
        if (computer == null) {
            return super.onContextItemSelected(item);
        }
        return performHostAction(item.getItemId(), computer);
    }

    private boolean performHostAction(int actionId, final ComputerObject computer) {
        switch (actionId) {
            case PAIR_ID:
                doPair(computer.details);
                return true;

            case UNPAIR_ID:
                doUnpair(computer.details);
                return true;

            case WOL_ID:
                doWakeOnLan(computer.details);
                return true;

            case DELETE_ID:
                if (ActivityManager.isUserAMonkey()) {
                    LimeLog.info("Ignoring delete PC request from monkey");
                    return true;
                }
                UiHelper.displayDeletePcConfirmationDialog(this, computer.details, new Runnable() {
                    @Override
                    public void run() {
                        if (managerBinder == null) {
                            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                            return;
                        }
                        removeComputer(computer.details);
                    }
                }, null);
                return true;

            case FULL_APP_LIST_ID:
                doAppList(computer.details, false, true);
                return true;

            case RESUME_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                ServerHelper.doStart(this, new NvApp("app", computer.details.runningGameId, false), computer.details, managerBinder);
                return true;

            case QUIT_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        ServerHelper.doQuit(PcView.this, computer.details,
                                new NvApp("app", 0, false), managerBinder, null);
                    }
                }, null);
                return true;

            case VIEW_DETAILS_ID:
                Dialog.displayDialog(PcView.this, getResources().getString(R.string.title_details), computer.details.toString(), false);
                return true;

            case TEST_NETWORK_ID:
                ServerHelper.doNetworkTest(PcView.this);
                return true;

            case GAMESTREAM_EOL_ID:
                HelpLauncher.launchGameStreamEolFaq(PcView.this);
                return true;

            default:
                return false;
        }
    }
    
    private void removeComputer(ComputerDetails details) {
        managerBinder.removeComputer(details);
        new LauncherLibraryStore(this).clearHost(details.uuid);

        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);

        // Delete hidden games preference value
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .remove(details.uuid)
                .apply();

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            if (details.equals(computer.details)) {
                // Disable or delete shortcuts referencing this PC
                shortcutHelper.disableComputerShortcut(details,
                        getResources().getString(R.string.scut_deleted_pc));

                pcGridAdapter.removeComputer(computer);
                pcGridAdapter.notifyDataSetChanged();
                updateHostsEmptyState(findViewById(R.id.hostHero));
                if (hostShelf != null) {
                    hostShelf.post(hostShelf::refreshHorizontalCentering);
                }

                break;
            }
        }
    }
    
    private void updateComputer(ComputerDetails details) {
        ComputerObject existingEntry = null;

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            // Check if this is the same computer
            if (details.uuid.equals(computer.details.uuid)) {
                existingEntry = computer;
                break;
            }
        }

        if (existingEntry != null) {
            // Replace the information in the existing entry
            existingEntry.details = details;
        }
        else {
            // Add a new entry
            pcGridAdapter.addComputer(new ComputerObject(details));

            updateHostsEmptyState(findViewById(R.id.hostHero));
            if (pcGridAdapter.getCount() == 1) {
                updateHostHero(pcGridAdapter.getItem(0));
                hostShelf.post(() -> {
                    View item = hostShelf.getLayoutManager() != null ?
                            hostShelf.getLayoutManager().findViewByPosition(0) : null;
                    View first = item == null ? null : item.findViewById(R.id.host_profile_avatar);
                    if (first != null) {
                        first.requestFocus();
                    }
                });
            }
        }

        // Notify the view that the data has changed
        pcGridAdapter.notifyDataSetChanged();
        if (hostShelf != null) {
            hostShelf.post(hostShelf::refreshHorizontalCentering);
        }
    }

    private void handleHostClick(ComputerObject computer, View view) {
        if (computer.details.state == ComputerDetails.State.UNKNOWN ||
                computer.details.state == ComputerDetails.State.OFFLINE) {
            showHostContextMenu(computer, view);
        }
        else if (computer.details.pairState != PairState.PAIRED) {
            doPair(computer.details);
        }
        else {
            doAppList(computer.details, false, false);
        }
    }

    private void showHostContextMenu(ComputerObject computer, View view) {
        contextComputer = computer;
        List<ConsoleActionPanel.Action> actions = new ArrayList<>();
        ComputerDetails details = computer.details;
        if (details.state == ComputerDetails.State.OFFLINE ||
                details.state == ComputerDetails.State.UNKNOWN) {
            actions.add(new ConsoleActionPanel.Action(WOL_ID,
                    getString(R.string.pcview_menu_send_wol)));
            actions.add(new ConsoleActionPanel.Action(GAMESTREAM_EOL_ID,
                    getString(R.string.pcview_menu_eol)));
        }
        else if (details.pairState != PairState.PAIRED) {
            actions.add(new ConsoleActionPanel.Action(PAIR_ID,
                    getString(R.string.pcview_menu_pair_pc)));
            if (details.nvidiaServer) {
                actions.add(new ConsoleActionPanel.Action(GAMESTREAM_EOL_ID,
                        getString(R.string.pcview_menu_eol)));
            }
        }
        else {
            if (details.runningGameId != 0) {
                actions.add(new ConsoleActionPanel.Action(RESUME_ID,
                        getString(R.string.applist_menu_resume)));
                actions.add(new ConsoleActionPanel.Action(QUIT_ID,
                        getString(R.string.applist_menu_quit), true));
            }
            actions.add(new ConsoleActionPanel.Action(FULL_APP_LIST_ID,
                    getString(R.string.pcview_menu_app_list)));
            actions.add(new ConsoleActionPanel.Action(UNPAIR_ID,
                    getString(R.string.pcview_menu_unpair_pc), true));
            if (details.nvidiaServer) {
                actions.add(new ConsoleActionPanel.Action(GAMESTREAM_EOL_ID,
                        getString(R.string.pcview_menu_eol)));
            }
        }
        actions.add(new ConsoleActionPanel.Action(TEST_NETWORK_ID,
                getString(R.string.pcview_menu_test_network)));
        actions.add(new ConsoleActionPanel.Action(VIEW_DETAILS_ID,
                getString(R.string.pcview_menu_details)));
        actions.add(new ConsoleActionPanel.Action(DELETE_ID,
                getString(R.string.pcview_menu_delete_pc), true));
        ConsoleActionPanel.show(this, details.name, actions,
                actionId -> performHostAction(actionId, computer));
    }

    private void updateHostHero(ComputerObject computer) {
        if (hostHeroTitle == null || hostHeroStatus == null) {
            return;
        }
        hostHeroTitle.animate().cancel();
        hostHeroStatus.animate().cancel();
        hostHeroTitle.setText(computer.details.name);
        if (computer.details.state == ComputerDetails.State.OFFLINE) {
            hostHeroStatus.setText(R.string.console_host_offline);
        }
        else if (computer.details.state == ComputerDetails.State.UNKNOWN) {
            hostHeroStatus.setText(R.string.console_host_refreshing);
        }
        else if (computer.details.pairState != PairState.PAIRED) {
            hostHeroStatus.setText(R.string.console_host_unpaired);
        }
        else if (computer.details.runningGameId != 0) {
            hostHeroStatus.setText(R.string.console_host_running);
        }
        else {
            hostHeroStatus.setText(R.string.console_host_open);
        }
        hostHeroTitle.setAlpha(0.72f);
        hostHeroStatus.setAlpha(0.72f);
        hostHeroTitle.animate().alpha(1f).setDuration(180).start();
        hostHeroStatus.animate().alpha(1f).setDuration(180).start();
    }

    public static class ComputerObject {
        public ComputerDetails details;

        public ComputerObject(ComputerDetails details) {
            if (details == null) {
                throw new IllegalArgumentException("details must not be null");
            }
            this.details = details;
        }

        @Override
        public String toString() {
            return details.name;
        }
    }
}
