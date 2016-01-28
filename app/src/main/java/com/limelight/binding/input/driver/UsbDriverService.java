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
import android.os.Build;
import android.os.IBinder;
import android.view.InputDevice;

import com.limelight.LimeLog;

import java.util.ArrayList;

public class UsbDriverService extends Service implements UsbDriverListener {

    private static final String ACTION_USB_PERMISSION =
            "com.limelight.USB_PERMISSION";

    private UsbManager usbManager;

    private final UsbEventReceiver receiver = new UsbEventReceiver();
    private final UsbDriverBinder binder = new UsbDriverBinder();

    private final ArrayList<AbstractController> controllers = new ArrayList<>();

    private UsbDriverListener listener;
    private int nextDeviceId;

    @Override
    public void reportControllerState(int controllerId, short buttonFlags, float leftStickX, float leftStickY, float rightStickX, float rightStickY, float leftTrigger, float rightTrigger) {
        // Call through to the client's listener
        if (listener != null) {
            listener.reportControllerState(controllerId, buttonFlags, leftStickX, leftStickY, rightStickX, rightStickY, leftTrigger, rightTrigger);
        }
    }

    @Override
    public void deviceRemoved(int controllerId) {
        // Remove the the controller from our list (if not removed already)
        for (AbstractController controller : controllers) {
            if (controller.getControllerId() == controllerId) {
                controllers.remove(controller);
                break;
            }
        }

        // Call through to the client's listener
        if (listener != null) {
            listener.deviceRemoved(controllerId);
        }
    }

    @Override
    public void deviceAdded(int controllerId) {
        // Call through to the client's listener
        if (listener != null) {
            listener.deviceAdded(controllerId);
        }
    }

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

            // Report all controllerMap that already exist
            if (listener != null) {
                for (AbstractController controller : controllers) {
                    listener.deviceAdded(controller.getControllerId());
                }
            }
        }
    }

    private void handleUsbDeviceState(UsbDevice device) {
        // Are we able to operate it?
        if (shouldClaimDevice(device)) {
            // Do we have permission yet?
            if (!usbManager.hasPermission(device)) {
                // Let's ask for permission
                usbManager.requestPermission(device, PendingIntent.getBroadcast(UsbDriverService.this, 0, new Intent(ACTION_USB_PERMISSION), 0));
                return;
            }

            // Open the device
            UsbDeviceConnection connection = usbManager.openDevice(device);
            if (connection == null) {
                LimeLog.warning("Unable to open USB device: "+device.getDeviceName());
                return;
            }


            AbstractController controller;

            if (XboxOneController.canClaimDevice(device)) {
                controller = new XboxOneController(device, connection, nextDeviceId++, this);
            }
            else if (Xbox360Controller.canClaimDevice(device)) {
                controller = new Xbox360Controller(device, connection, nextDeviceId++, this);
            }
            else {
                // Unreachable
                return;
            }

            // Start the controller
            if (!controller.start()) {
                connection.close();
                return;
            }

            // Add this controller to the list
            controllers.add(controller);
        }
    }

    private boolean isRecognizedInputDevice(UsbDevice device) {
        // On KitKat and later, we can determine if this VID and PID combo
        // matches an existing input device and defer to the built-in controller
        // support in that case. Prior to KitKat, we'll always return true to be safe.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice inputDev = InputDevice.getDevice(id);

                if (inputDev.getVendorId() == device.getVendorId() &&
                        inputDev.getProductId() == device.getProductId()) {
                    return true;
                }
            }

            return false;
        }
        else {
            return true;
        }
    }

    private boolean shouldClaimDevice(UsbDevice device) {
        // We always bind to XB1 controllers but only bind to XB360 controllers
        // if we know the kernel isn't already driving this device.
        return XboxOneController.canClaimDevice(device) ||
                (!isRecognizedInputDevice(device) && Xbox360Controller.canClaimDevice(device));
    }

    @Override
    public void onCreate() {
        this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Register for USB attach broadcasts and permission completions
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(receiver, filter);

        // Enumerate existing devices
        for (UsbDevice dev : usbManager.getDeviceList().values()) {
            if (shouldClaimDevice(dev)) {
                // Start the process of claiming this device
                handleUsbDeviceState(dev);
            }
        }
    }

    @Override
    public void onDestroy() {
        // Stop the attachment receiver
        unregisterReceiver(receiver);

        // Remove listeners
        listener = null;

        // Stop all controllers
        while (controllers.size() > 0) {
            // Stop and remove the controller
            controllers.remove(0).stop();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
