package com.limelight.utils;

import android.net.TrafficStats;

public class TrafficStatsHelper {
    public static long getAllRxBytes() {
        return TrafficStats.getTotalRxBytes();
    }

    public static long getAllTxBytes() {
        return TrafficStats.getTotalTxBytes();
    }

    public static long getAllRxBytesMobile() {
        return TrafficStats.getMobileRxBytes();
    }

    public static long getAllTxBytesMobile() {
        return TrafficStats.getMobileTxBytes();
    }

    public static long getAllRxBytesWifi() {
        return TrafficStats.getTotalRxBytes() - TrafficStats.getMobileRxBytes();
    }

    public static long getAllTxBytesWifi() {
        return TrafficStats.getTotalTxBytes() - TrafficStats.getMobileTxBytes();
    }

    public static long getPackageRxBytes(int uid) {
        return TrafficStats.getUidRxBytes(uid);
    }

    public static long getPackageTxBytes(int uid) {
        return TrafficStats.getUidTxBytes(uid);
    }
}