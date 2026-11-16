package com.limelight.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import java.net.Inet4Address;
import java.net.InetAddress;

public class NetHelper {
    public static boolean isActiveNetworkVpn(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connMgr.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities netCaps = connMgr.getNetworkCapabilities(activeNetwork);
                if (netCaps != null) {
                    return netCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                            !netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
                }
            }
        }
        else {
            NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                return activeNetworkInfo.getType() == ConnectivityManager.TYPE_VPN;
            }
        }

        return false;
    }

    /**
     * Parses a dotted-quad IPv4 literal without touching DNS.
     *
     * @return the four address bytes, or null if the string is not an IPv4 literal
     */
    public static byte[] parseIpv4Literal(String address) {
        if (address == null) {
            return null;
        }

        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }

        byte[] result = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }

            int value = 0;
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (c < '0' || c > '9') {
                    return null;
                }
                value = value * 10 + (c - '0');
            }

            if (value > 255) {
                return null;
            }
            result[i] = (byte) value;
        }

        return result;
    }

    /**
     * Whether the address is in 100.64.0.0/10 (RFC 6598 shared address space).
     *
     * Tailscale assigns its addresses from this range. An address here describes
     * how a client reached a host, not where the host sits on its home network.
     */
    public static boolean isSharedAddressSpace(String address) {
        byte[] ip = parseIpv4Literal(address);
        return ip != null && (ip[0] & 0xFF) == 100 && (ip[1] & 0xC0) == 0x40;
    }

    /**
     * Whether the first prefixLength bits of the two IPv4 addresses match.
     */
    public static boolean isSameIpv4Subnet(byte[] a, byte[] b, int prefixLength) {
        // Ignore nonsense and overly broad prefixes so a bogus link can't match everything
        if (a == null || b == null || a.length != 4 || b.length != 4 || prefixLength < 8 || prefixLength > 32) {
            return false;
        }

        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }

        if (remainingBits != 0) {
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            if ((a[fullBytes] & mask) != (b[fullBytes] & mask)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Whether this device is attached to the network that contains the given host address.
     *
     * Only real Wi-Fi and Ethernet connections count. A VPN such as Tailscale can carry a
     * route for the host's subnet from anywhere, so it says nothing about whether the device
     * is actually at home. Hostnames and IPv6 addresses can't be judged this way, so they
     * report true to leave the normal address order alone.
     */
    @SuppressWarnings("deprecation")
    public static boolean isOnLocalNetworkOf(Context context, String address) {
        byte[] target = parseIpv4Literal(address);
        if (target == null) {
            return true;
        }

        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) {
            return true;
        }

        Network[] networks = connMgr.getAllNetworks();
        if (networks == null) {
            return true;
        }

        for (Network network : networks) {
            NetworkCapabilities caps = connMgr.getNetworkCapabilities(network);
            if (caps == null) {
                continue;
            }

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                continue;
            }

            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                continue;
            }

            LinkProperties props = connMgr.getLinkProperties(network);
            if (props == null) {
                continue;
            }

            for (LinkAddress linkAddress : props.getLinkAddresses()) {
                InetAddress ip = linkAddress.getAddress();
                if (ip instanceof Inet4Address &&
                        isSameIpv4Subnet(ip.getAddress(), target, linkAddress.getPrefixLength())) {
                    return true;
                }
            }
        }

        return false;
    }
}
