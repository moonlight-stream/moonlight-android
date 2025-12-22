package com.limelight.wincaster;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.PairingManager;

import java.security.cert.CertificateEncodingException;

public class WinCasterDeepLinkActivity extends Activity {
    private static final String TAG = "WinCasterDeepLink";

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private String host;
    private String uuid;
    private String name;
    private String app;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    (ComputerManagerService.ComputerManagerBinder) binder;

            // Wait in a separate thread to avoid stalling the UI
            new Thread(() -> {
                localBinder.waitForReady();
                managerBinder = localBinder;
                runOnUiThread(() -> processDeepLink());
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri data = intent.getData();

        if (data == null) {
            LimeLog.warning(TAG + ": No URI data provided");
            Toast.makeText(this, "Invalid deep link", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        LimeLog.info(TAG + ": Processing deep link: " + data.toString());

        // Parse query parameters
        host = data.getQueryParameter("host");
        uuid = data.getQueryParameter("uuid");
        name = data.getQueryParameter("name");
        app = data.getQueryParameter("app");

        if (host == null && uuid == null) {
            LimeLog.warning(TAG + ": Missing host or uuid parameter");
            Toast.makeText(this, "Missing host or uuid parameter", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Bind to ComputerManagerService
        bindService(new Intent(this, ComputerManagerService.class),
                serviceConnection, Service.BIND_AUTO_CREATE);
    }

    private void processDeepLink() {
        if (managerBinder == null) {
            Toast.makeText(this, getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Find the computer
        ComputerDetails computer = null;

        if (uuid != null && !uuid.isEmpty()) {
            computer = managerBinder.getComputer(uuid);
        }

        if (computer == null && host != null && !host.isEmpty()) {
            // Try to find by host address
            for (ComputerDetails details : managerBinder.getComputers()) {
                if (details.activeAddress != null && host.equals(details.activeAddress.address)) {
                    computer = details;
                    break;
                }
                if (details.localAddress != null && host.equals(details.localAddress.address)) {
                    computer = details;
                    break;
                }
                if (details.remoteAddress != null && host.equals(details.remoteAddress.address)) {
                    computer = details;
                    break;
                }
            }
        }

        if (computer == null) {
            LimeLog.warning(TAG + ": Host not found in paired computers");
            Toast.makeText(this, getString(R.string.scut_pc_not_found), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (computer.pairState != PairingManager.PairState.PAIRED) {
            LimeLog.warning(TAG + ": Host is not paired");
            Toast.makeText(this, getString(R.string.scut_not_paired), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            LimeLog.warning(TAG + ": Host is offline");
            Toast.makeText(this, getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Determine app ID
        int appId = 0;
        if (app != null && !app.isEmpty()) {
            try {
                appId = Integer.parseInt(app);
            } catch (NumberFormatException e) {
                // Non-numeric app ID, use running game or 0
                if (computer.runningGameId != 0) {
                    appId = computer.runningGameId;
                }
            }
        } else if (computer.runningGameId != 0) {
            appId = computer.runningGameId;
        }

        NvApp nvApp = new NvApp(app != null ? app : "Desktop", appId, false);

        // Start streaming
        Intent streamIntent = new Intent(this, Game.class);
        streamIntent.putExtra(Game.EXTRA_HOST, computer.activeAddress.address);
        streamIntent.putExtra(Game.EXTRA_PORT, computer.activeAddress.port);
        streamIntent.putExtra(Game.EXTRA_HTTPS_PORT, computer.httpsPort);
        streamIntent.putExtra(Game.EXTRA_APP_NAME, nvApp.getAppName());
        streamIntent.putExtra(Game.EXTRA_APP_ID, nvApp.getAppId());
        streamIntent.putExtra(Game.EXTRA_APP_HDR, nvApp.isHdrSupported());
        streamIntent.putExtra(Game.EXTRA_UNIQUEID, managerBinder.getUniqueId());
        streamIntent.putExtra(Game.EXTRA_PC_UUID, computer.uuid);
        streamIntent.putExtra(Game.EXTRA_PC_NAME, computer.name);

        try {
            if (computer.serverCert != null) {
                streamIntent.putExtra(Game.EXTRA_SERVER_CERT, computer.serverCert.getEncoded());
            }
        } catch (CertificateEncodingException e) {
            LimeLog.warning(TAG + ": Failed to encode server certificate: " + e.getMessage());
        }

        LimeLog.info(TAG + ": Starting stream to " + computer.name);
        startActivity(streamIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }
}
