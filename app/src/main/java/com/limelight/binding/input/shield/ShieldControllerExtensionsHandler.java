package com.limelight.binding.input.shield;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.input.InputManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.InputDevice;

import com.limelight.LimeLog;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShieldControllerExtensionsHandler implements InputManager.InputDeviceListener {
    private Context context;

    private IBinder binder;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            binder = iBinder;

            try {
                listenerId = registerListener();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            listenerId = 0;
            tokenToDeviceIdMap.clear();
            deviceIdToTokenMap.clear();

            binder = null;
        }
    };

    // ConcurrentHashMap handles synchronization between the Binder thread adding/removing
    // entries and callers on arbitrary threads that are doing device lookups.
    //
    // Since these are separate maps, they can be temporarily inconsistent (only one-way
    // of the two-way mapping present). This is fine for our purposes here.
    private ConcurrentHashMap<String, Integer> tokenToDeviceIdMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, String> deviceIdToTokenMap = new ConcurrentHashMap<>();
    private AtomicBoolean needsRefresh = new AtomicBoolean(false);

    private int listenerId;
    private IExposedControllerManagerListener.Stub controllerListener = new IExposedControllerManagerListener.Stub() {
        @Override
        public void onDeviceAdded(String controllerToken) {
            try {
                int inputDeviceId = getInputDeviceId(controllerToken);

                LimeLog.info("Shield controller added: " + controllerToken + " -> " + inputDeviceId);

                tokenToDeviceIdMap.put(controllerToken, inputDeviceId);
                deviceIdToTokenMap.put(inputDeviceId, controllerToken);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDeviceChanged(String controllerToken, int i) {
            LimeLog.info("Shield controller changed: " + controllerToken + " " + i);
        }

        @Override
        public void onDeviceRemoved(String controllerToken) {
            LimeLog.info("Shield controller removed: " + controllerToken);

            Integer deviceId = tokenToDeviceIdMap.remove(controllerToken);
            if (deviceId != null) {
                deviceIdToTokenMap.remove(deviceId);
            }
        }
    };

    public ShieldControllerExtensionsHandler(Context context) {
        this.context = context;

        InputManager inputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(this, null);

        Intent intent = new Intent();
        intent.setClassName("com.nvidia.blakepairing", "com.nvidia.blakepairing.AccessoryService");
        try {
            // The docs say to call unbindService() even if the bindService() call returns false
            // or throws a SecurityException.
            if (!context.bindService(intent, serviceConnection, Service.BIND_AUTO_CREATE)) {
                LimeLog.info("com.nvidia.blakepairing.AccessoryService is not available on this device");
                context.unbindService(serviceConnection);
            }
        } catch (SecurityException e) {
            context.unbindService(serviceConnection);
        }
    }

    private String getControllerToken(InputDevice device) {
        // Refresh device ID <-> token mappings if one of our devices was removed
        if (needsRefresh.compareAndSet(true, false)) {
            try {
                LimeLog.info("Refreshing controller token mappings");

                // We have to enumerate tokenToDeviceIdMap rather than deviceIdToTokenMap
                // because we remove the deviceIdToTokenMap entry when the device goes away.
                HashMap<String, Integer> newTokenToDeviceIdMap = new HashMap<>();
                HashMap<Integer, String> newDeviceIdToTokenMap = new HashMap<>();
                for (String existingToken : tokenToDeviceIdMap.keySet()) {
                    int deviceId = getInputDeviceId(existingToken);
                    if (deviceId != 0) {
                        newTokenToDeviceIdMap.put(existingToken, deviceId);
                        newDeviceIdToTokenMap.put(deviceId, existingToken);
                    }
                }

                tokenToDeviceIdMap.clear();
                deviceIdToTokenMap.clear();

                tokenToDeviceIdMap.putAll(newTokenToDeviceIdMap);
                deviceIdToTokenMap.putAll(newDeviceIdToTokenMap);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        return deviceIdToTokenMap.get(device.getId());
    }

    public boolean rumble(InputDevice device, int lowFreqMotor, int highFreqMotor) {
        String controllerToken = getControllerToken(device);
        if (controllerToken != null) {
            try {
                return rumble(controllerToken, lowFreqMotor, highFreqMotor);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public void destroy() {
        InputManager inputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        inputManager.unregisterInputDeviceListener(this);

        tokenToDeviceIdMap.clear();
        deviceIdToTokenMap.clear();

        if (listenerId != 0) {
            try {
                unregisterListener(listenerId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            listenerId = 0;
        }

        if (binder != null) {
            context.unbindService(serviceConnection);
            binder = null;
        }
    }

    private int registerListener() throws RemoteException {
        if (binder == null) {
            return 0;
        }

        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken("com.nvidia.blakepairing.IExposedControllerBinder");
            input.writeStrongBinder(controllerListener);

            binder.transact(20, input, output, 0);

            output.readException();
            return output.readInt();
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    private boolean unregisterListener(int listenerId) throws RemoteException {
        if (binder == null) {
            return false;
        }

        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken("com.nvidia.blakepairing.IExposedControllerBinder");
            input.writeInt(listenerId);

            binder.transact(21, input, output, 0);

            output.readException();
            return output.readInt() != 0;
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    private int getInputDeviceId(String controllerToken) throws RemoteException {
        if (binder == null) {
            return 0;
        }

        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken("com.nvidia.blakepairing.IExposedControllerBinder");
            input.writeString(controllerToken);

            binder.transact(13, input, output, 0);

            output.readException();
            return output.readInt();
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    // Rumble duration maximum of 1 second
    private boolean rumble(String controllerToken, int lowFreqMotor, int highFreqMotor) throws RemoteException {
        if (binder == null) {
            return false;
        }

        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken("com.nvidia.blakepairing.IExposedControllerBinder");
            input.writeString(controllerToken);
            input.writeInt(lowFreqMotor);
            input.writeInt(highFreqMotor);

            binder.transact(18, input, output, 0);

            output.readException();
            return output.readInt() != 0;
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    // Rumble duration maximum of 1.5 seconds
    private boolean rumbleWithDuration(String controllerToken, int lowFreqMotor, int highFreqMotor, long durationMs) throws RemoteException {
        if (binder == null) {
            return false;
        }

        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken("com.nvidia.blakepairing.IExposedControllerBinder");
            input.writeString(controllerToken);
            input.writeInt(lowFreqMotor);
            input.writeInt(highFreqMotor);
            input.writeLong(durationMs);

            binder.transact(19, input, output, 0);

            output.readException();
            return output.readInt() != 0;
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {}

    @Override
    public void onInputDeviceChanged(int deviceId) {}

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        // Remove the device ID to token mapping, but leave the token mapping to device ID
        // mapping so we will re-enumerate it when we next try to rumble a controller.
        if (deviceIdToTokenMap.remove(deviceId) != null) {
            needsRefresh.set(true);
        }
    }
}
