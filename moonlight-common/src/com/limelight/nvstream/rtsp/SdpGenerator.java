package com.limelight.nvstream.rtsp;

import java.net.Inet6Address;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;

public class SdpGenerator {
	private static void addSessionAttribute(StringBuilder config, String attribute, String value) {
		config.append("a="+attribute+":"+value+" \r\n");
	}
	
    private static void addSessionAttributeBytes(StringBuilder config, String attribute, byte[] value) {
        char str[] = new char[value.length];
        
        for (int i = 0; i < value.length; i++) {
            str[i] = (char)value[i];
        }
        
        addSessionAttribute(config, attribute, new String(str));
    }
    
    private static void addSessionAttributeInt(StringBuilder config, String attribute, int value) {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        b.putInt(value);
        addSessionAttributeBytes(config, attribute, b.array());
    }
	
	private static void addGen3Attributes(StringBuilder config, ConnectionContext context) {
		addSessionAttribute(config, "x-nv-general.serverAddress", context.serverAddress.getHostAddress());
		
		addSessionAttributeInt(config, "x-nv-general.featureFlags", 0x42774141);
        
		addSessionAttributeInt(config, "x-nv-video[0].transferProtocol", 0x41514141);
		addSessionAttributeInt(config, "x-nv-video[1].transferProtocol", 0x41514141);
		addSessionAttributeInt(config, "x-nv-video[2].transferProtocol", 0x41514141);
		addSessionAttributeInt(config, "x-nv-video[3].transferProtocol", 0x41514141);
		
		addSessionAttributeInt(config, "x-nv-video[0].rateControlMode", 0x42414141);
		addSessionAttributeInt(config, "x-nv-video[1].rateControlMode", 0x42514141);
		addSessionAttributeInt(config, "x-nv-video[2].rateControlMode", 0x42514141);
		addSessionAttributeInt(config, "x-nv-video[3].rateControlMode", 0x42514141);
		
        addSessionAttribute(config, "x-nv-vqos[0].bw.flags", "14083");
        
        addSessionAttribute(config, "x-nv-vqos[0].videoQosMaxConsecutiveDrops", "0");
        addSessionAttribute(config, "x-nv-vqos[1].videoQosMaxConsecutiveDrops", "0");
        addSessionAttribute(config, "x-nv-vqos[2].videoQosMaxConsecutiveDrops", "0");
        addSessionAttribute(config, "x-nv-vqos[3].videoQosMaxConsecutiveDrops", "0");
	}
	
	private static void addGen4Attributes(StringBuilder config, ConnectionContext context) {
		addSessionAttribute(config, "x-nv-general.serverAddress", "rtsp://"+context.serverAddress.getHostAddress()+":48010");

		addSessionAttribute(config, "x-nv-video[0].rateControlMode", "4");
		
		// Use slicing for increased performance on some decoders
		addSessionAttribute(config, "x-nv-video[0].videoEncoderSlicesPerFrame", "4");
		
		// Enable surround sound if configured for it
		addSessionAttribute(config, "x-nv-audio.surround.numChannels", ""+context.streamConfig.getAudioChannelCount());
		addSessionAttribute(config, "x-nv-audio.surround.channelMask", ""+context.streamConfig.getAudioChannelMask());
		if (context.streamConfig.getAudioChannelCount() > 2) {
			addSessionAttribute(config, "x-nv-audio.surround.enable", "1");
		}
		else {
			addSessionAttribute(config, "x-nv-audio.surround.enable", "0");
		}
	}
	
	public static String generateSdpFromContext(ConnectionContext context) {
		StringBuilder config = new StringBuilder();
		config.append("v=0").append("\r\n"); // SDP Version 0
		config.append("o=android 0 "+RtspConnection.getRtspVersionFromContext(context)+" IN ");
		if (context.serverAddress instanceof Inet6Address) {
			config.append("IPv6 ");
		}
		else {
			config.append("IPv4 ");
		}
		config.append(context.serverAddress.getHostAddress());
		config.append("\r\n");
		config.append("s=NVIDIA Streaming Client").append("\r\n");
				
		addSessionAttribute(config, "x-nv-video[0].clientViewportWd", ""+context.streamConfig.getWidth());
		addSessionAttribute(config, "x-nv-video[0].clientViewportHt", ""+context.streamConfig.getHeight());
		addSessionAttribute(config, "x-nv-video[0].maxFPS", ""+context.streamConfig.getRefreshRate());
		
		addSessionAttribute(config, "x-nv-video[0].packetSize", ""+context.streamConfig.getMaxPacketSize());
		
		if (context.streamConfig.getRemote()) {
			addSessionAttribute(config, "x-nv-video[0].averageBitrate", "4");
			addSessionAttribute(config, "x-nv-video[0].peakBitrate", "4");
		}
		
		addSessionAttribute(config, "x-nv-video[0].timeoutLengthMs", "7000");
		addSessionAttribute(config, "x-nv-video[0].framesWithInvalidRefThreshold", "0");
				
		// Lock the bitrate if we're not scaling resolution so the picture doesn't get too bad
		if (context.streamConfig.getHeight() >= 2160 && context.streamConfig.getRefreshRate() >= 60) {
			if (context.streamConfig.getBitrate() < 80000) {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+context.streamConfig.getBitrate());
			}
			else {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", "80000");
			}
		}
		else if (context.streamConfig.getHeight() >= 2160) {
			if (context.streamConfig.getBitrate() < 40000) {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+context.streamConfig.getBitrate());
			}
			else {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", "40000");
			}
		}
		else if (context.streamConfig.getHeight() >= 1080 && context.streamConfig.getRefreshRate() >= 60) {
			if (context.streamConfig.getBitrate() < 20000) {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+context.streamConfig.getBitrate());
			}
			else {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", "20000");
			}
		}
		else if (context.streamConfig.getHeight() >= 1080 || context.streamConfig.getRefreshRate() >= 60) {
			if (context.streamConfig.getBitrate() < 10000) {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+context.streamConfig.getBitrate());
			}
			else {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", "10000");
			}
		}
		else {
			if (context.streamConfig.getBitrate() < 5000) {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+context.streamConfig.getBitrate());
			}
			else {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", "5000");
			}
		}
		
		addSessionAttribute(config, "x-nv-vqos[0].bw.maximumBitrate", ""+context.streamConfig.getBitrate());
		
		// Using FEC turns padding on which makes us have to take the slow path
		// in the depacketizer, not to mention exposing some ambiguous cases with
		// distinguishing padding from valid sequences. Since we can only perform
		// execute an FEC recovery on a 1 packet frame, we'll just turn it off completely.
		addSessionAttribute(config, "x-nv-vqos[0].fec.enable", "0");
		
		addSessionAttribute(config, "x-nv-vqos[0].videoQualityScoreUpdateTime", "5000");
		
		if (context.streamConfig.getRemote()) {
			addSessionAttribute(config, "x-nv-vqos[0].qosTrafficType", "0");
		}
		else {
			addSessionAttribute(config, "x-nv-vqos[0].qosTrafficType", "5");
		}
		
		if (context.streamConfig.getRemote()) {
			addSessionAttribute(config, "x-nv-aqos.qosTrafficType", "0");
		}
		else {
			addSessionAttribute(config, "x-nv-aqos.qosTrafficType", "4");
		}
		
		// Add generation-specific attributes
		switch (context.serverGeneration) {
		case ConnectionContext.SERVER_GENERATION_3:
			addGen3Attributes(config, context);
			break;
			
		case ConnectionContext.SERVER_GENERATION_4:
		default:
			addGen4Attributes(config, context);
			break;
		}

		config.append("t=0 0").append("\r\n");
		
		if (context.serverGeneration == ConnectionContext.SERVER_GENERATION_3) {
			config.append("m=video 47996  ").append("\r\n");
		}
		else {
			config.append("m=video 47998  ").append("\r\n");
		}
		
		return config.toString();
	}
}
