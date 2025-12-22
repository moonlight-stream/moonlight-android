package com.limelight.wincaster;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import com.limelight.LimeLog;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

public class WinCasterAdvertiser {
    private static final String TAG = "WinCasterAdvertiser";
    private static final String SERVICE_TYPE = "_wincaster._tcp.";
    private static final String SERVICE_NAME = "WinCaster";
    private static final int SERVICE_PORT = 47990;
    private static final String VERSION = "1.0.0";

    private final Context context;
    private final AtomicBoolean isAdvertising = new AtomicBoolean(false);

    // NsdManager implementation (Android 14+)
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;

    // JmDNS implementation (older Android versions)
    private JmDNS jmdns;
    private WifiManager.MulticastLock multicastLock;
    private Thread jmdnsThread;

    public WinCasterAdvertiser(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        if (isAdvertising.getAndSet(true)) {
            LimeLog.info(TAG + ": Already advertising");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Use NsdManager on Android 14+ (API 34)
            startNsdAdvertising();
        } else {
            // Use JmDNS on older versions
            startJmdnsAdvertising();
        }
    }

    public void stop() {
        if (!isAdvertising.getAndSet(false)) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            stopNsdAdvertising();
        } else {
            stopJmdnsAdvertising();
        }
    }

    private void startNsdAdvertising() {
        LimeLog.info(TAG + ": Starting NsdManager advertising");

        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) {
            LimeLog.warning(TAG + ": NsdManager not available");
            isAdvertising.set(false);
            return;
        }

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(getServiceName());
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(SERVICE_PORT);

        // Add TXT records
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            serviceInfo.setAttribute("name", Build.MODEL);
            serviceInfo.setAttribute("version", VERSION);
            serviceInfo.setAttribute("platform", "android");
        }

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                LimeLog.info(TAG + ": Service registered: " + serviceInfo.getServiceName());
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                LimeLog.warning(TAG + ": Registration failed: " + errorCode);
                isAdvertising.set(false);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                LimeLog.info(TAG + ": Service unregistered");
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                LimeLog.warning(TAG + ": Unregistration failed: " + errorCode);
            }
        };

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        } catch (Exception e) {
            LimeLog.warning(TAG + ": Failed to register service: " + e.getMessage());
            isAdvertising.set(false);
        }
    }

    private void stopNsdAdvertising() {
        LimeLog.info(TAG + ": Stopping NsdManager advertising");

        if (nsdManager != null && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception e) {
                LimeLog.warning(TAG + ": Failed to unregister service: " + e.getMessage());
            }
        }
        nsdManager = null;
        registrationListener = null;
    }

    private void startJmdnsAdvertising() {
        LimeLog.info(TAG + ": Starting JmDNS advertising");

        jmdnsThread = new Thread(() -> {
            try {
                // Acquire multicast lock
                WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    multicastLock = wifi.createMulticastLock("WinCasterAdvertiser");
                    multicastLock.setReferenceCounted(true);
                    multicastLock.acquire();
                }

                // Get local IP address
                InetAddress localAddress = getLocalIpAddress();
                if (localAddress == null) {
                    LimeLog.warning(TAG + ": Could not determine local IP address");
                    isAdvertising.set(false);
                    return;
                }

                // Create JmDNS instance
                jmdns = JmDNS.create(localAddress, getServiceName());

                // Create TXT record map
                Map<String, String> txtRecords = new HashMap<>();
                txtRecords.put("name", Build.MODEL);
                txtRecords.put("version", VERSION);
                txtRecords.put("platform", "android");

                // Register service
                ServiceInfo serviceInfo = ServiceInfo.create(
                        SERVICE_TYPE + "local.",
                        getServiceName(),
                        SERVICE_PORT,
                        0, // weight
                        0, // priority
                        txtRecords
                );

                jmdns.registerService(serviceInfo);
                LimeLog.info(TAG + ": JmDNS service registered");

            } catch (IOException e) {
                LimeLog.warning(TAG + ": JmDNS error: " + e.getMessage());
                isAdvertising.set(false);
            }
        });
        jmdnsThread.start();
    }

    private void stopJmdnsAdvertising() {
        LimeLog.info(TAG + ": Stopping JmDNS advertising");

        if (jmdns != null) {
            try {
                jmdns.unregisterAllServices();
                jmdns.close();
            } catch (IOException e) {
                LimeLog.warning(TAG + ": Error closing JmDNS: " + e.getMessage());
            }
            jmdns = null;
        }

        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
            multicastLock = null;
        }

        if (jmdnsThread != null) {
            jmdnsThread.interrupt();
            jmdnsThread = null;
        }
    }

    private String getServiceName() {
        // Use device model as service name, sanitized for DNS
        String name = Build.MODEL.replaceAll("[^a-zA-Z0-9\\-]", "-");
        if (name.length() > 63) {
            name = name.substring(0, 63);
        }
        return SERVICE_NAME + "-" + name;
    }

    private InetAddress getLocalIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
                if (ipAddress != 0) {
                    byte[] bytes = new byte[]{
                            (byte) (ipAddress & 0xff),
                            (byte) (ipAddress >> 8 & 0xff),
                            (byte) (ipAddress >> 16 & 0xff),
                            (byte) (ipAddress >> 24 & 0xff)
                    };
                    return InetAddress.getByAddress(bytes);
                }
            }
        } catch (Exception e) {
            LimeLog.warning(TAG + ": Error getting local IP: " + e.getMessage());
        }
        return null;
    }

    public boolean isAdvertising() {
        return isAdvertising.get();
    }
}
