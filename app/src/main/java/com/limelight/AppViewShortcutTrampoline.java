package com.limelight;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import java.util.UUID;

public class AppViewShortcutTrampoline extends Activity {
    private String uuidString;

    private ComputerDetails computer;
    private SpinnerDialog blockingLoadSpinner;

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

                    // Force CMS to repoll this machine
                    managerBinder.invalidateStateForComputer(computer.uuid);

                    // Start polling
                    managerBinder.startPolling(new ComputerManagerListener() {
                        @Override
                        public void notifyComputerUpdated(final ComputerDetails details) {
                            // Don't care about other computers
                            if (!details.uuid.toString().equalsIgnoreCase(uuidString)) {
                                return;
                            }

                            if (details.state != ComputerDetails.State.UNKNOWN) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Stop showing the spinner
                                        if (blockingLoadSpinner != null) {
                                            blockingLoadSpinner.dismiss();
                                            blockingLoadSpinner = null;
                                        }

                                        if (details.state == ComputerDetails.State.ONLINE) {
                                            // Close this activity
                                            finish();

                                            if (details.runningGameId != 0) {
                                                // A game is running so launch straight to the game activity
                                                ServerHelper.doStart(AppViewShortcutTrampoline.this,
                                                        new NvApp("app", details.runningGameId), details, managerBinder);
                                            }
                                            else {
                                                // No game running - launch to the AppView
                                                Intent i = new Intent(getIntent());
                                                i.setClass(AppViewShortcutTrampoline.this, AppView.class);
                                                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                                startActivity(i);
                                            }
                                        }
                                        else if (details.state == ComputerDetails.State.OFFLINE) {
                                            // Computer offline - display an error dialog
                                            Dialog.displayDialog(AppViewShortcutTrampoline.this,
                                                    getResources().getString(R.string.conn_error_title),
                                                    getResources().getString(R.string.error_pc_offline),
                                                    true);
                                        }

                                        // We don't want any more callbacks from now on
                                        managerBinder.stopPolling();
                                        managerBinder = null;
                                    }
                                });
                            }
                        }
                    });
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiHelper.notifyNewRootView(this);

        uuidString = getIntent().getStringExtra(UUID_EXTRA);

        // Bind to the computer manager service
        bindService(new Intent(this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        blockingLoadSpinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.conn_establishing_title),
                getResources().getString(R.string.applist_connect_msg), true);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (blockingLoadSpinner != null) {
            blockingLoadSpinner.dismiss();
            blockingLoadSpinner = null;
        }

        Dialog.closeDialogs();

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }

        if (managerBinder != null) {
            managerBinder.stopPolling();
        }

        finish();
    }
}
