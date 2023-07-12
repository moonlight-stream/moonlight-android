package com.limelight.nvstream.mdns;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import com.limelight.LimeLog;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class NsdManagerDiscoveryAgent extends MdnsDiscoveryAgent {
    private static final String SERVICE_TYPE = "_nvstream._tcp";
    private NsdManager nsdManager;
    private boolean discoveryActive;
    private boolean wantsDiscoveryActive;
    private final HashMap<String, NsdManager.ServiceInfoCallback> serviceCallbacks = new HashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    private NsdManager.DiscoveryListener discoveryListener = new NsdManager.DiscoveryListener() {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            discoveryActive = false;
            LimeLog.severe("NSD: Service discovery start failed: " + errorCode);
            listener.notifyDiscoveryFailure(new RuntimeException("onStartDiscoveryFailed(): " + errorCode));
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            LimeLog.severe("NSD: Service discovery stop failed: " + errorCode);
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            discoveryActive = true;
            LimeLog.info("NSD: Service discovery started");

            // If we were stopped before we could finish starting, stop now
            if (!wantsDiscoveryActive) {
                stopDiscovery();
            }
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            discoveryActive = false;
            LimeLog.info("NSD: Service discovery stopped");

            // If we were started before we could finish stopping, start again now
            if (wantsDiscoveryActive) {
                startDiscovery(0);
            }
        }

        @Override
        public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
            LimeLog.info("NSD: Machine appeared: "+nsdServiceInfo.getServiceName());

            NsdManager.ServiceInfoCallback serviceInfoCallback = new NsdManager.ServiceInfoCallback() {
                @Override
                public void onServiceInfoCallbackRegistrationFailed(int errorCode) {
                    LimeLog.severe("NSD: Service info callback registration failed: " + errorCode);
                    listener.notifyDiscoveryFailure(new RuntimeException("onServiceInfoCallbackRegistrationFailed(): " + errorCode));
                }

                @Override
                public void onServiceUpdated(NsdServiceInfo nsdServiceInfo) {
                    LimeLog.info("NSD: Machine resolved: "+nsdServiceInfo.getServiceName());
                    reportNewComputer(nsdServiceInfo.getServiceName(), nsdServiceInfo.getPort(),
                            getV4Addrs(nsdServiceInfo.getHostAddresses()),
                            getV6Addrs(nsdServiceInfo.getHostAddresses()));
                }

                @Override
                public void onServiceLost() {}

                @Override
                public void onServiceInfoCallbackUnregistered() {}
            };

            nsdManager.registerServiceInfoCallback(nsdServiceInfo, executor, serviceInfoCallback);
            serviceCallbacks.put(nsdServiceInfo.getServiceName(), serviceInfoCallback);
        }

        @Override
        public void onServiceLost(NsdServiceInfo nsdServiceInfo) {
            LimeLog.info("NSD: Machine lost: "+nsdServiceInfo.getServiceName());

            NsdManager.ServiceInfoCallback serviceInfoCallback = serviceCallbacks.remove(nsdServiceInfo.getServiceName());
            if (serviceInfoCallback != null) {
                nsdManager.unregisterServiceInfoCallback(serviceInfoCallback);
            }
        }
    };

    public NsdManagerDiscoveryAgent(Context context, MdnsDiscoveryListener listener) {
        super(listener);
        this.nsdManager = context.getSystemService(NsdManager.class);
    }

    @Override
    public void startDiscovery(int discoveryIntervalMs) {
        wantsDiscoveryActive = true;

        // Register the service discovery listener
        if (!discoveryActive) {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        }
    }

    @Override
    public void stopDiscovery() {
        wantsDiscoveryActive = false;

        // Unregister the service discovery listener
        if (discoveryActive) {
            nsdManager.stopServiceDiscovery(discoveryListener);
        }

        // Unregister all service info callbacks
        for (NsdManager.ServiceInfoCallback callback : serviceCallbacks.values()) {
            nsdManager.unregisterServiceInfoCallback(callback);
        }
        serviceCallbacks.clear();
    }

    private static Inet4Address[] getV4Addrs(List<InetAddress> addrs) {
        int matchCount = 0;
        for (InetAddress addr : addrs) {
            if (addr instanceof Inet4Address) {
                matchCount++;
            }
        }

        Inet4Address[] matching = new Inet4Address[matchCount];

        int i = 0;
        for (InetAddress addr : addrs) {
            if (addr instanceof Inet4Address) {
                matching[i++] = (Inet4Address) addr;
            }
        }

        return matching;
    }

    private static Inet6Address[] getV6Addrs(List<InetAddress> addrs) {
        int matchCount = 0;
        for (InetAddress addr : addrs) {
            if (addr instanceof Inet6Address) {
                matchCount++;
            }
        }

        Inet6Address[] matching = new Inet6Address[matchCount];

        int i = 0;
        for (InetAddress addr : addrs) {
            if (addr instanceof Inet6Address) {
                matching[i++] = (Inet6Address) addr;
            }
        }

        return matching;
    }
}
