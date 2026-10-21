package com.limelight.binding.input.driver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import com.limelight.LimeLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bluetooth LE driver for the 2026 Steam Controller (Valve codename "Triton").
 *
 * The controller does not use HID over GATT, so Android does not expose it as a gamepad. Instead
 * it has a custom Valve GATT service. Input reports arrive as notifications on one characteristic,
 * and output reports (such as rumble) are written to per-report characteristics.
 *
 * The controller must already be paired with the device in the system Bluetooth settings. We connect
 * to paired controllers with autoConnect so they attach whenever they are switched on.
 */
@SuppressLint("MissingPermission") // Callers check BLUETOOTH_CONNECT before creating this class
public class SteamTritonBleController extends AbstractSteamTritonController {
    public static final int PRODUCT_ID_BLE = 0x1303;

    private static final String NAME_PREFIX = "Steam Ctrl";

    private static final UUID SERVICE_UUID = UUID.fromString("100F6C32-1735-4313-B402-38567131E5F3");
    private static final UUID INPUT_CHARACTERISTIC_UUID = UUID.fromString("100F6C7A-1735-4313-B402-38567131E5F3");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Feature reports (such as the one that turns off lizard mode) are written here
    private static final UUID REPORT_CHARACTERISTIC_UUID = UUID.fromString("100F6C34-1735-4313-B402-38567131E5F3");

    // Output report N is written to the characteristic whose UUID is 100F6C(N + 0x35)-...
    private static final Pattern OUTPUT_CHARACTERISTIC_PATTERN =
            Pattern.compile("^100F6C([0-9A-F]{2})-1735-4313-B402-38567131E5F3$");
    private static final int OUTPUT_UUID_OFFSET = 0x35;
    private static final int MIN_OUTPUT_REPORT_ID = 0x80;

    // The controller needs Data Length Extensions, which come with a larger MTU
    private static final int REQUESTED_MTU = 517;

    private static final long ENABLE_NOTIFICATIONS_FALLBACK_MS = 1000;
    private static final long ENABLE_NOTIFICATIONS_RETRY_MS = 1000;
    private static final int ENABLE_NOTIFICATIONS_MAX_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 2000;
    private static final int GATT_ERROR = 133;

    // The controller stops rumbling after ~50 ms without a new command, so we resend while active.
    private static final long RUMBLE_REFRESH_INTERVAL_MS = 40;
    private static final long WRITE_TIMEOUT_MS = 500;
    private static final long LIZARD_WRITE_RETRY_MS = 100;

    private final Context context;
    private final BluetoothDevice device;

    private final Map<Integer, BluetoothGattCharacteristic> outputCharacteristics = new ConcurrentHashMap<>();
    private volatile BluetoothGattCharacteristic inputCharacteristic;
    private volatile BluetoothGattCharacteristic reportCharacteristic;
    private volatile BluetoothGatt gatt;

    private HandlerThread handlerThread;
    private Handler handler;

    private volatile boolean notificationsRequested;
    private volatile int notificationAttempts;

    private volatile boolean writePending;
    private volatile long lastWriteTime;

    private volatile int rumbleLow, rumbleHigh;

    @SuppressLint("MissingPermission")
    public static boolean canClaimDevice(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name != null && name.startsWith(NAME_PREFIX);
        } catch (SecurityException e) {
            return false;
        }
    }

    public static boolean hasBluetoothPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }

        // The legacy BLUETOOTH permission is granted at install time
        return true;
    }

    /**
     * Returns the paired Steam Controllers, or an empty list if Bluetooth is unavailable, switched
     * off, or we don't have permission to use it.
     */
    @SuppressLint("MissingPermission")
    public static List<BluetoothDevice> getBondedControllers(Context context) {
        ArrayList<BluetoothDevice> controllers = new ArrayList<>();

        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ||
                !hasBluetoothPermission(context)) {
            return controllers;
        }

        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
            if (adapter == null || !adapter.isEnabled()) {
                return controllers;
            }

            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice dev : bonded) {
                    if (canClaimDevice(dev)) {
                        controllers.add(dev);
                    }
                }
            }
        } catch (SecurityException e) {
            LimeLog.warning("Not allowed to list Bluetooth devices: " + e.getMessage());
        }

        return controllers;
    }

    public SteamTritonBleController(Context context, BluetoothDevice device, int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, VALVE_VENDOR_ID, PRODUCT_ID_BLE);
        this.context = context.getApplicationContext();
        this.device = device;
    }

    @Override
    public boolean start() {
        handlerThread = new HandlerThread("SteamTritonBle");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        return connect();
    }

    private boolean connect() {
        if (stopped) {
            return false;
        }

        try {
            // autoConnect lets the controller attach whenever it is switched on, even if it
            // wasn't when we started.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                gatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
            }
            else {
                gatt = device.connectGatt(context, true, gattCallback);
            }
        } catch (SecurityException e) {
            LimeLog.warning("Not allowed to connect to Steam Controller: " + e.getMessage());
            gatt = null;
        }

        if (gatt == null) {
            LimeLog.warning("Unable to start GATT connection to Steam Controller");
            return false;
        }

        return true;
    }

    private void resetControllerState() {
        synchronized (stateLock) {
            buttonFlags = 0;
            leftTrigger = rightTrigger = 0;
            leftStickX = leftStickY = rightStickX = rightStickY = 0;
        }
    }

    private void handleDisconnected() {
        releaseHeldInput();

        notificationsRequested = false;
        notificationAttempts = 0;
        writePending = false;
        inputCharacteristic = null;
        reportCharacteristic = null;
        outputCharacteristics.clear();
        handler.removeCallbacks(lizardModeRunnable);

        if (added) {
            added = false;
            resetControllerState();
            notifyDeviceRemoved();
        }
    }

    private final Runnable enableNotificationsRunnable = new Runnable() {
        @Override
        public void run() {
            enableNotifications();
        }
    };

    // Turns off lizard mode and keeps it off. Without this the controller also acts as a
    // keyboard and mouse, which Android would forward to the host alongside the gamepad input.
    private final Runnable lizardModeRunnable = new Runnable() {
        @Override
        public void run() {
            BluetoothGatt g = gatt;
            BluetoothGattCharacteristic characteristic = reportCharacteristic;
            if (stopped || g == null || characteristic == null) {
                return;
            }

            // Writes are one at a time, so try again shortly if one is still on its way
            long now = SystemClock.uptimeMillis();
            if (writePending && now - lastWriteTime < WRITE_TIMEOUT_MS) {
                handler.postDelayed(this, LIZARD_WRITE_RETRY_MS);
                return;
            }

            // The characteristic takes the report without its leading ID byte
            byte[] report = buildLizardModeOffReport();
            byte[] payload = Arrays.copyOfRange(report, 1, report.length);

            writePending = true;
            lastWriteTime = now;
            if (!writeCharacteristic(g, characteristic, payload)) {
                writePending = false;
                LimeLog.warning("Unable to turn off Steam Controller lizard mode");
            }

            handler.postDelayed(this, LIZARD_REFRESH_INTERVAL_MS);
        }
    };

    private final Runnable rumbleRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            int low = rumbleLow, high = rumbleHigh;
            if (stopped || (low == 0 && high == 0)) {
                return;
            }

            sendRumble(low, high, false);
            handler.postDelayed(this, RUMBLE_REFRESH_INTERVAL_MS);
        }
    };

    private void enableNotifications() {
        BluetoothGatt g = gatt;
        BluetoothGattCharacteristic input = inputCharacteristic;
        if (stopped || g == null || input == null || notificationsRequested) {
            return;
        }
        notificationsRequested = true;

        boolean started = false;
        BluetoothGattDescriptor cccd = input.getDescriptor(CCCD_UUID);
        if (cccd != null && g.setCharacteristicNotification(input, true)) {
            byte[] value = (input.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ?
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE :
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
            started = writeDescriptor(g, cccd, value);
        }

        if (!started) {
            LimeLog.warning("Unable to enable Steam Controller notifications");
            scheduleNotificationRetry();
        }
    }

    private void scheduleNotificationRetry() {
        notificationsRequested = false;
        if (++notificationAttempts < ENABLE_NOTIFICATIONS_MAX_ATTEMPTS && !stopped) {
            handler.postDelayed(enableNotificationsRunnable, ENABLE_NOTIFICATIONS_RETRY_MS);
        }
        else {
            LimeLog.warning("Giving up enabling Steam Controller notifications");
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean writeDescriptor(BluetoothGatt g, BluetoothGattDescriptor descriptor, byte[] value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return g.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS;
        }

        descriptor.setValue(value);
        return g.writeDescriptor(descriptor);
    }

    @SuppressWarnings("deprecation")
    private static boolean writeCharacteristic(BluetoothGatt g, BluetoothGattCharacteristic characteristic, byte[] value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return g.writeCharacteristic(characteristic, value, characteristic.getWriteType()) == BluetoothStatusCodes.SUCCESS;
        }

        characteristic.setValue(value);
        return g.writeCharacteristic(characteristic);
    }

    private void handleInputNotification(BluetoothGattCharacteristic characteristic, byte[] value) {
        if (value == null || !INPUT_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
            return;
        }

        // Notifications carry just the payload, so put the report ID back in front
        byte[] report = new byte[value.length + 1];
        report[0] = (byte) REPORT_ID_STATE_BLE;
        System.arraycopy(value, 0, report, 1, value.length);
        processInputReport(report, report.length);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (stopped || (gatt != null && g != gatt)) {
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                LimeLog.info("Steam Controller connected over Bluetooth");
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        BluetoothGatt current = gatt;
                        if (!stopped && current != null) {
                            current.discoverServices();
                        }
                    }
                });
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                LimeLog.info("Steam Controller disconnected from Bluetooth (status " + status + ")");
                handleDisconnected();

                if (status == GATT_ERROR) {
                    // The stack can get stuck after this error, so start over with a fresh connection
                    g.close();
                    gatt = null;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            connect();
                        }
                    }, RECONNECT_DELAY_MS);
                }
                // Otherwise autoConnect will bring the connection back when the controller returns
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (stopped || status != BluetoothGatt.GATT_SUCCESS) {
                LimeLog.warning("Steam Controller service discovery failed: " + status);
                return;
            }

            BluetoothGattService service = g.getService(SERVICE_UUID);
            if (service == null) {
                LimeLog.warning("Steam Controller GATT service not found");
                return;
            }

            outputCharacteristics.clear();
            reportCharacteristic = null;
            BluetoothGattCharacteristic input = null;
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                UUID uuid = characteristic.getUuid();
                if (INPUT_CHARACTERISTIC_UUID.equals(uuid)) {
                    input = characteristic;
                    continue;
                }
                if (REPORT_CHARACTERISTIC_UUID.equals(uuid)) {
                    reportCharacteristic = characteristic;
                    continue;
                }

                Matcher matcher = OUTPUT_CHARACTERISTIC_PATTERN.matcher(uuid.toString().toUpperCase(Locale.ROOT));
                if (matcher.matches()) {
                    int reportId = Integer.parseInt(matcher.group(1), 16) - OUTPUT_UUID_OFFSET;
                    if (reportId >= MIN_OUTPUT_REPORT_ID) {
                        outputCharacteristics.put(reportId, characteristic);
                    }
                }
            }

            if (input == null) {
                LimeLog.warning("Steam Controller input characteristic not found");
                return;
            }
            inputCharacteristic = input;

            notificationsRequested = false;
            notificationAttempts = 0;

            // Ask for the larger MTU first, then enable notifications once it's settled. Some stacks
            // never call back, so also enable notifications after a timeout.
            if (g.requestMtu(REQUESTED_MTU)) {
                handler.postDelayed(enableNotificationsRunnable, ENABLE_NOTIFICATIONS_FALLBACK_MS);
            }
            else {
                handler.post(enableNotificationsRunnable);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            LimeLog.info("Steam Controller MTU: " + mtu + " (status " + status + ")");
            handler.post(enableNotificationsRunnable);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
            if (stopped || !CCCD_UUID.equals(descriptor.getUuid())) {
                return;
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                LimeLog.warning("Enabling Steam Controller notifications failed: " + status);
                scheduleNotificationRetry();
                return;
            }

            // Switch the controller out of keyboard and mouse mode, and keep it out
            handler.removeCallbacks(lizardModeRunnable);
            handler.post(lizardModeRunnable);

            if (!added) {
                // Report that we're added _before_ reporting input
                notifyDeviceAdded();
                added = true;
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {
            writePending = false;
        }

        // Called on Android 13 and later
        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic, byte[] value) {
            handleInputNotification(characteristic, value);
        }

        // Called before Android 13
        @Override
        @SuppressWarnings("deprecation")
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleInputNotification(characteristic, characteristic.getValue());
            }
        }
    };

    private void sendRumble(int low, int high, boolean force) {
        BluetoothGatt g = gatt;
        BluetoothGattCharacteristic characteristic = outputCharacteristics.get(REPORT_ID_RUMBLE);
        if (g == null || characteristic == null || !added) {
            return;
        }

        // Writes are one at a time, so skip this one if the last is still on its way
        long now = SystemClock.uptimeMillis();
        if (!force && writePending && now - lastWriteTime < WRITE_TIMEOUT_MS) {
            return;
        }

        // The characteristic takes the report without its leading ID byte
        byte[] report = buildRumbleReport(low, high);
        byte[] payload = Arrays.copyOfRange(report, 1, report.length);

        writePending = true;
        lastWriteTime = now;
        if (!writeCharacteristic(g, characteristic, payload)) {
            writePending = false;
        }
    }

    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        int low = lowFreqMotor & 0xFFFF;
        int high = highFreqMotor & 0xFFFF;

        rumbleLow = low;
        rumbleHigh = high;

        if (stopped || handler == null) {
            return;
        }

        // Send right away; the refresh loop keeps it going while non-zero
        handler.removeCallbacks(rumbleRefreshRunnable);
        handler.post(rumbleRefreshRunnable);
        if (low == 0 && high == 0) {
            sendRumble(0, 0, true);
        }
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }

        // Cancel any rumble effects while the connection is still open
        rumbleLow = 0;
        rumbleHigh = 0;
        sendRumble(0, 0, true);

        releaseHeldInput();

        boolean wasAdded = added;
        stopped = true;
        added = false;

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        BluetoothGatt g = gatt;
        gatt = null;
        if (g != null) {
            g.disconnect();
            g.close();
        }

        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }

        // If we were already disconnected, the host has been told we're gone
        if (wasAdded) {
            notifyDeviceRemoved();
        }
    }
}
