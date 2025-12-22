package com.limelight.wincaster;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.cert.CertificateEncodingException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WinCasterCommandService extends Service {
    private static final String TAG = "WinCasterCommandService";
    private static final int TCP_PORT = 47990;
    private static final String NOTIFICATION_CHANNEL_ID = "wincaster_service";
    private static final int NOTIFICATION_ID = 1;

    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean isRunning = false;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean isBound = false;

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public WinCasterCommandService getService() {
            return WinCasterCommandService.this;
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            managerBinder = (ComputerManagerService.ComputerManagerBinder) service;
            isBound = true;
            LimeLog.info(TAG + ": Connected to ComputerManagerService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            managerBinder = null;
            isBound = false;
            LimeLog.info(TAG + ": Disconnected from ComputerManagerService");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        LimeLog.info(TAG + ": Service created");

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        // Bind to ComputerManagerService
        Intent intent = new Intent(this, ComputerManagerService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        executorService = Executors.newCachedThreadPool();
        startServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LimeLog.info(TAG + ": Service started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LimeLog.info(TAG + ": Service destroyed");

        stopServer();

        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }

        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "WinCaster Remote Control",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("WinCaster remote control service");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, PcView.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("WinCaster")
                .setContentText("Remote control service running")
                .setSmallIcon(R.drawable.ic_pc)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void startServer() {
        isRunning = true;
        executorService.submit(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                LimeLog.info(TAG + ": TCP server listening on port " + TCP_PORT);

                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        LimeLog.info(TAG + ": Client connected from " + clientSocket.getInetAddress());
                        executorService.submit(() -> handleClient(clientSocket));
                    } catch (SocketException e) {
                        if (isRunning) {
                            LimeLog.warning(TAG + ": Socket exception: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                LimeLog.warning(TAG + ": Failed to start server: " + e.getMessage());
            }
        });
    }

    private void stopServer() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LimeLog.warning(TAG + ": Error closing server socket: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true)) {

            clientSocket.setSoTimeout(30000); // 30 second timeout

            String line;
            while ((line = reader.readLine()) != null) {
                LimeLog.info(TAG + ": Received: " + line);
                String response = processCommand(line);
                writer.println(response);
                LimeLog.info(TAG + ": Sent: " + response);
            }
        } catch (IOException e) {
            LimeLog.warning(TAG + ": Client handling error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                LimeLog.warning(TAG + ": Error closing client socket: " + e.getMessage());
            }
        }
    }

    private String processCommand(String jsonCommand) {
        try {
            JSONObject request = new JSONObject(jsonCommand);
            String command = request.optString("command", "");

            switch (command) {
                case "ping":
                    return createResponse("pong", null);

                case "status":
                    return getStatus();

                case "stream":
                    return startStream(request);

                case "stop":
                    return stopStream();

                default:
                    return createErrorResponse("Unknown command: " + command);
            }
        } catch (JSONException e) {
            return createErrorResponse("Invalid JSON: " + e.getMessage());
        }
    }

    private String getStatus() {
        try {
            JSONObject response = new JSONObject();
            response.put("status", "ready");
            response.put("clientName", Build.MODEL);
            response.put("version", "1.0.0");
            response.put("platform", "android");
            return response.toString();
        } catch (JSONException e) {
            return createErrorResponse("Failed to create status response");
        }
    }

    private String startStream(JSONObject request) {
        if (!isBound || managerBinder == null) {
            return createErrorResponse("ComputerManagerService not available");
        }

        String host = request.optString("host", "");
        String hostUUID = request.optString("hostUUID", "");
        String hostName = request.optString("hostName", "");
        String appId = request.optString("appId", "");

        if (host.isEmpty()) {
            return createErrorResponse("Missing 'host' parameter");
        }

        // Wait for the manager to be ready
        managerBinder.waitForReady();

        // Find the computer by UUID or add a new one
        ComputerDetails computer = null;

        if (!hostUUID.isEmpty()) {
            computer = managerBinder.getComputer(hostUUID);
        }

        if (computer == null) {
            // Computer not found, we need to add it
            return createErrorResponse("Host not found. Please pair the host first using the app.");
        }

        if (computer.pairState != com.limelight.nvstream.http.PairingManager.PairState.PAIRED) {
            return createErrorResponse("Host is not paired. Please pair the host first using the app.");
        }

        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            return createErrorResponse("Host is offline");
        }

        // Create an NvApp for the stream
        int appIdInt = 0;
        if (!appId.isEmpty()) {
            try {
                appIdInt = Integer.parseInt(appId);
            } catch (NumberFormatException e) {
                // If appId is "Desktop" or non-numeric, use the running game or default to Desktop
                if (computer.runningGameId != 0) {
                    appIdInt = computer.runningGameId;
                }
            }
        } else if (computer.runningGameId != 0) {
            appIdInt = computer.runningGameId;
        }

        NvApp app = new NvApp(appId.isEmpty() ? "Desktop" : appId, appIdInt, false);

        // Create intent to start streaming
        Intent intent = new Intent(this, Game.class);
        intent.putExtra(Game.EXTRA_HOST, computer.activeAddress.address);
        intent.putExtra(Game.EXTRA_PORT, computer.activeAddress.port);
        intent.putExtra(Game.EXTRA_HTTPS_PORT, computer.httpsPort);
        intent.putExtra(Game.EXTRA_APP_NAME, app.getAppName());
        intent.putExtra(Game.EXTRA_APP_ID, app.getAppId());
        intent.putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported());
        intent.putExtra(Game.EXTRA_UNIQUEID, managerBinder.getUniqueId());
        intent.putExtra(Game.EXTRA_PC_UUID, computer.uuid);
        intent.putExtra(Game.EXTRA_PC_NAME, computer.name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            if (computer.serverCert != null) {
                intent.putExtra(Game.EXTRA_SERVER_CERT, computer.serverCert.getEncoded());
            }
        } catch (CertificateEncodingException e) {
            LimeLog.warning(TAG + ": Failed to encode server certificate: " + e.getMessage());
        }

        startActivity(intent);

        try {
            JSONObject response = new JSONObject();
            response.put("status", "streaming");
            response.put("clientName", Build.MODEL);
            response.put("host", computer.name);
            return response.toString();
        } catch (JSONException e) {
            return createErrorResponse("Stream started but failed to create response");
        }
    }

    private String stopStream() {
        // Send broadcast to stop the stream
        Intent intent = new Intent("com.limelight.STOP_STREAM");
        sendBroadcast(intent);

        return createResponse("stopped", null);
    }

    private String createResponse(String status, String message) {
        try {
            JSONObject response = new JSONObject();
            response.put("status", status);
            if (message != null) {
                response.put("message", message);
            }
            return response.toString();
        } catch (JSONException e) {
            return "{\"status\":\"error\",\"message\":\"Failed to create response\"}";
        }
    }

    private String createErrorResponse(String message) {
        try {
            JSONObject response = new JSONObject();
            response.put("status", "error");
            response.put("message", message);
            return response.toString();
        } catch (JSONException e) {
            return "{\"status\":\"error\",\"message\":\"" + message + "\"}";
        }
    }
}
