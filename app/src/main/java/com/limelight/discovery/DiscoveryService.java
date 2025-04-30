package com.limelight.discovery;

import java.util.List;

import com.limelight.nvstream.mdns.MdnsComputer;
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent;
import com.limelight.nvstream.mdns.MdnsDiscoveryAgent;
import com.limelight.nvstream.mdns.MdnsDiscoveryListener;
import com.limelight.nvstream.mdns.NsdManagerDiscoveryAgent;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

public class DiscoveryService extends Service {

    private MdnsDiscoveryAgent discoveryAgent;
    private MdnsDiscoveryListener boundListener;

    public class DiscoveryBinder extends Binder {
        public void setListener(MdnsDiscoveryListener listener) {
            boundListener = listener;
        }

        public void startDiscovery(int queryIntervalMs) {
            discoveryAgent.startDiscovery(queryIntervalMs);
        }

        public void stopDiscovery() {
            discoveryAgent.stopDiscovery();
        }

        public List<MdnsComputer> getComputerSet() {
            return discoveryAgent.getComputerSet();
        }
    }

    @Override
    public void onCreate() {
        MdnsDiscoveryListener listener = new MdnsDiscoveryListener() {
            @Override
            public void notifyComputerAdded(MdnsComputer computer) {
                if (boundListener != null) {
                    boundListener.notifyComputerAdded(computer);
                }
            }

            @Override
            public void notifyDiscoveryFailure(Exception e) {
                if (boundListener != null) {
                    boundListener.notifyDiscoveryFailure(e);
                }
            }
        };

        // Prior to Android 14, NsdManager doesn't provide all the capabilities needed for parity
        // with jmDNS (specifically handling multiple addresses for a single service). There are
        // also documented reliability bugs early in the Android 4.x series shortly after it was
        // introduced. The benefit of using NsdManager over jmDNS is that it works correctly in
        // environments where mDNS proxying is required, like ChromeOS, WSA, and the emulator.
        //
        // As such, we use the jmDNS-based MdnsDiscoveryAgent prior to Android 14 and NsdManager
        // on Android 14 and above.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            discoveryAgent = new JmDNSDiscoveryAgent(getApplicationContext(), listener);
        }
        else {
            discoveryAgent = new NsdManagerDiscoveryAgent(getApplicationContext(), listener);
        }
    }

    private final DiscoveryBinder binder = new DiscoveryBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Stop any discovery session
        discoveryAgent.stopDiscovery();

        // Unbind the listener
        boundListener = null;
        return false;
    }
}
