package com.limelight.grid.assets;

import android.content.Context;

import com.limelight.LimeLog;
import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

public class NetworkAssetLoader {
    private final Context context;
    private final String uniqueId;

    public NetworkAssetLoader(Context context, String uniqueId) {
        this.context = context;
        this.uniqueId = uniqueId;
    }

    public InputStream getBitmapStream(CachedAppAssetLoader.LoaderTuple tuple) {
        NvHTTP http = new NvHTTP(getCurrentAddress(tuple.computer), uniqueId, null, PlatformBinding.getCryptoProvider(context));

        InputStream in = null;
        try {
            in = http.getBoxArt(tuple.app);
        } catch (IOException e) {}

        if (in != null) {
            LimeLog.info("Network asset load complete: " + tuple);
        }
        else {
            LimeLog.info("Network asset load failed: " + tuple);
        }

        return in;
    }

    private static InetAddress getCurrentAddress(ComputerDetails computer) {
        if (computer.reachability == ComputerDetails.Reachability.LOCAL) {
            return computer.localIp;
        }
        else {
            return computer.remoteIp;
        }
    }
}
