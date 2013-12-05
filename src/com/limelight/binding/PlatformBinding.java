package com.limelight.binding;

import android.os.Build;

import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.binding.video.AndroidCpuDecoderRenderer;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;

public class PlatformBinding {
	public static VideoDecoderRenderer chooseDecoderRenderer() {
		if (Build.HARDWARE.equals("goldfish")) {
			// Emulator - don't render video (it's slow!)
			return null;
		}
		/*else if (MediaCodecDecoderRenderer.findSafeDecoder() != null) {
			// Hardware decoding
			return new MediaCodecDecoderRenderer();
		}*/
		else {
			// Software decoding
			return new AndroidCpuDecoderRenderer();
		}
	}
	
	public static String getDeviceName() {
		String deviceName = android.os.Build.MODEL;
        deviceName = deviceName.replace(" ", "");
        return deviceName;
	}
	
	public static AudioRenderer getAudioRenderer() {
		return new AndroidAudioRenderer();
	}
}
