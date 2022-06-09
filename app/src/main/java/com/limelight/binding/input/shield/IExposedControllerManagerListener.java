package com.limelight.binding.input.shield;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IExposedControllerManagerListener extends IInterface {
    void onDeviceAdded(String controllerToken);
    void onDeviceChanged(String controllerToken, int i);
    void onDeviceRemoved(String controllerToken);

    public static abstract class Stub extends Binder implements IExposedControllerManagerListener {
        public Stub() {
            attachInterface(this, "com.nvidia.blakepairing.IExposedControllerManagerListener");
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel input, Parcel output, int flags) throws RemoteException {
            switch (code) {
                case 1:
                    input.enforceInterface("com.nvidia.blakepairing.IExposedControllerManagerListener");
                    onDeviceAdded(input.readString());
                    break;
                case 2:
                    input.enforceInterface("com.nvidia.blakepairing.IExposedControllerManagerListener");
                    onDeviceChanged(input.readString(), input.readInt());
                    break;
                case 3:
                    input.enforceInterface("com.nvidia.blakepairing.IExposedControllerManagerListener");
                    onDeviceRemoved(input.readString());
                    break;
                case 4:
                case 5:
                    input.enforceInterface("com.nvidia.blakepairing.IExposedControllerManagerListener");
                    // Don't care
                    break;

                default:
                    return super.onTransact(code, input, output, flags);
            }

            return true;
        }
    }
}
