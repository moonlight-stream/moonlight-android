package com.limelight.binding;

import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.nvstream.av.audio.AudioRenderer;

public class PlatformBinding {
	public static String getDeviceName() {
		String deviceName = android.os.Build.MODEL;
        deviceName = deviceName.replace(" ", "");
        return deviceName;
	}
	
	public static AudioRenderer getAudioRenderer() {
		return new AndroidAudioRenderer();
	}
}
