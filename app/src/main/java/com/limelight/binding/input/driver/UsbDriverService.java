package com.limelight.binding.input.driver;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.IBinder;

import java.util.ArrayList;

public class UsbDriverService extends Service {

    private static final String ACTION_USB_PERMISSION =
            "com.limelight.USB_PERMISSION";

    private UsbManager usbManager;

    private final UsbEventReceiver receiver = new UsbEventReceiver();
    private final UsbDriverBinder binder = new UsbDriverBinder();

    private final ArrayList<XboxOneController> controllers = new ArrayList<>();

    private UsbDriverListener listener;
    private static int nextDeviceId;

    public class UsbEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // Initial attachment broadcast
            if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                // Continue the state machine
                handleUsbDeviceState(device);
            }
            // Subsequent permission dialog completion intent
            else if (action.equals(ACTION_USB_PERMISSION)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                // If we got this far, we've already found we're able to handle this device
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    handleUsbDeviceState(device);
                }
            }
        }
    }

    public class UsbDriverBinder extends Binder {
        public void setListener(UsbDriverListener listener) {
            UsbDriverService.this.listener = listener;
            updateListeners();
        }
    }

    private void updateListeners() {
        for (XboxOneController controller : controllers) {
            controller.setListener(listener);
        }
    }

    private void handleUsbDeviceState(UsbDevice device) {
        // Are we able to operate it?
        if (XboxOneController.canClaimDevice(device)) {
            // Do we have permission yet?
            if (!usbManager.hasPermission(device)) {
                // Let's ask for permission
                usbManager.requestPermission(device, PendingIntent.getBroadcast(UsbDriverService.this, 0, new Intent(ACTION_USB_PERMISSION), 0));
                return;
            }

            // Open the device
            UsbDeviceConnection connection = usbManager.openDevice(device);

            // Try to initialize it
            XboxOneController controller = new XboxOneController(device, connection, nextDeviceId++);
            if (!controller.start()) {
                connection.close();
                return;
            }

            // Add to the list
            controllers.add(controller);

            // Add listener
            updateListeners();
        }
    }

    @Override
    public void onCreate() {
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Register for USB attach broadcasts and permission completions
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(receiver, filter);

        // Enumerate existing devices
        for (UsbDevice dev : usbManager.getDeviceList().values()) {
            if (XboxOneController.canClaimDevice(dev)) {
                // Start the process of claiming this device
                handleUsbDeviceState(dev);
            }
        }
    }

    @Override
    public void onDestroy() {
        // Stop the attachment receiver
        unregisterReceiver(receiver);

        // Remove all listeners
        listener = null;
        updateListeners();

        // Stop all controllers
        for (XboxOneController controller : controllers) {
            controller.stop();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
