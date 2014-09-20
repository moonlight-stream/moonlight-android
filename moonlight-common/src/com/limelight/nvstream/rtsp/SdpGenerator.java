package com.limelight.nvstream.rtsp;

import java.net.InetAddress;
import java.net.Inet6Address;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


import com.limelight.nvstream.StreamConfiguration;

public class SdpGenerator {
	private static void addSessionAttributeBytes(StringBuilder config, String attribute, byte[] value) {
		char str[] = new char[value.length];
		
		for (int i = 0; i < value.length; i++) {
			str[i] = (char)value[i];
		}
		
		addSessionAttribute(config, attribute, new String(str));
	}
	
	private static void addSessionAttributeInt(StringBuilder config, String attribute, int value) {
		ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(value);
		addSessionAttributeBytes(config, attribute, b.array());
	}
	
	private static void addSessionAttribute(StringBuilder config, String attribute, String value) {
		config.append("a="+attribute+":"+value+" \r\n");
	}
	
	public static String generateSdpFromConfig(InetAddress host, StreamConfiguration sc) {
		StringBuilder config = new StringBuilder();
		config.append("v=0").append("\r\n"); // SDP Version 0
		config.append("o=android 0 "+RtspConnection.CLIENT_VERSION+" IN ");
		if (host instanceof Inet6Address) {
			config.append("IPv6 ");
		}
		else {
			config.append("IPv4 ");
		}
		config.append(host.getHostAddress());
		config.append("\r\n");
		config.append("s=NVIDIA Streaming Client").append("\r\n");
		
		addSessionAttribute(config, "x-nv-general.serverAddress", host.getHostAddress());
		
		addSessionAttributeInt(config, "x-nv-general.featureFlags", 0x42774141);
		
		addSessionAttribute(config, "x-nv-video[0].clientViewportWd", ""+sc.getWidth());
		addSessionAttribute(config, "x-nv-video[0].clientViewportHt", ""+sc.getHeight());
		addSessionAttribute(config, "x-nv-video[0].maxFPS", ""+sc.getRefreshRate());
		
		addSessionAttributeInt(config, "x-nv-video[0].transferProtocol", 0x41514120);
		addSessionAttributeInt(config, "x-nv-video[1].transferProtocol", 0x41514120);
		addSessionAttributeInt(config, "x-nv-video[2].transferProtocol", 0x41514120);
		addSessionAttributeInt(config, "x-nv-video[3].transferProtocol", 0x41514120);

		addSessionAttributeInt(config, "x-nv-video[0].rateControlMode", 0x42414141);
		addSessionAttributeInt(config, "x-nv-video[1].rateControlMode", 0x42514141);
		addSessionAttributeInt(config, "x-nv-video[2].rateControlMode", 0x42514141);
		addSessionAttributeInt(config, "x-nv-video[3].rateControlMode", 0x42514141);
		
		addSessionAttribute(config, "x-nv-video[0].timeoutLengthMs", "7000");
		addSessionAttribute(config, "x-nv-video[0].framesWithInvalidRefThreshold", "0");
		
		// It should be 16183 but adding 100 but causes resolution to scale in the beginning
		// The bit 0x80 enables video scaling on packet loss which we can't support (for now)
		addSessionAttribute(config, "x-nv-vqos[0].bw.flags", "16083");
		
		addSessionAttribute(config, "x-nv-vqos[0].bw.maximumBitrate", ""+sc.getBitrate());
		
		// Since we can only deal with FEC data on a 1 packet frame,
		// restrict FEC repair percentage to minimum so we get only 1
		// FEC packet per frame
		addSessionAttribute(config, "x-nv-vqos[0].fec.repairPercent", "1");
		addSessionAttribute(config, "x-nv-vqos[0].fec.repairMaxPercent", "1");
		addSessionAttribute(config, "x-nv-vqos[0].fec.repairMinPercent", "1");
		
		addSessionAttribute(config, "x-nv-vqos[0].videoQualityScoreUpdateTime", "5000");
		addSessionAttribute(config, "x-nv-vqos[0].qosTrafficType", "5");
		
		addSessionAttribute(config, "x-nv-vqos[0].videoQosMaxConsecutiveDrops", "0");
		addSessionAttribute(config, "x-nv-vqos[1].videoQosMaxConsecutiveDrops", "0");
		addSessionAttribute(config, "x-nv-vqos[2].videoQosMaxConsecutiveDrops", "0");
		addSessionAttribute(config, "x-nv-vqos[3].videoQosMaxConsecutiveDrops", "0");
		
		addSessionAttribute(config, "x-nv-aqos.qosTrafficType", "8");

		config.append("t=0 0").append("\r\n");
		
		config.append("m=video 47996  ").append("\r\n");
		
		return config.toString();
	}
}
