package com.limelight.binding;

import android.content.Context;

import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.http.LimelightCryptoProvider;

public class PlatformBinding {
    public static String getDeviceName() {
        String deviceName = android.os.Build.MODEL;
        deviceName = deviceName.replace(" ", "");
        return deviceName;
    }

    public static AudioRenderer getAudioRenderer() {
        return new AndroidAudioRenderer();
    }

    public static LimelightCryptoProvider getCryptoProvider(Context c) {
        return new AndroidCryptoProvider(c);
    }
}
